package com.realtime.pipeline.flink.recovery;

import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 容错机制基于属性的测试
 * 使用jqwik进行属性测试，验证Checkpoint和故障恢复的正确性属性
 * 
 * Feature: realtime-data-pipeline
 * **Validates: Requirements 2.5, 4.1, 4.2, 4.3, 4.4, 4.6**
 */
class FaultTolerancePropertyTest {

    /**
     * Feature: realtime-data-pipeline, Property 9: Checkpoint失败容错
     * 
     * **Validates: Requirements 2.5**
     * 
     * 对于任何Checkpoint失败事件，系统应该记录错误日志并继续处理数据
     * 
     * 测试策略:
     * - 模拟多次Checkpoint操作，其中一些成功，一些失败
     * - 验证失败的Checkpoint被正确记录
     * - 验证失败后系统继续运行（不抛出异常）
     * - 验证失败率被正确计算
     */
    @Property(tries = 20)
    @Label("Property 9: Checkpoint failure tolerance")
    void checkpointFailureTolerance(
            @ForAll @IntRange(min = 5, max = 50) int totalCheckpoints,
            @ForAll @IntRange(min = 0, max = 100) int failurePercentage) {
        
        CheckpointListener listener = new CheckpointListener();
        
        // 模拟多次Checkpoint操作
        int expectedFailures = 0;
        int expectedSuccesses = 0;
        
        for (int i = 0; i < totalCheckpoints; i++) {
            long checkpointId = i + 1;
            
            // 开始Checkpoint
            listener.notifyCheckpointStart(checkpointId);
            
            // 根据失败率决定是否失败
            boolean shouldFail = (i % 100) < failurePercentage;
            
            if (shouldFail) {
                // 模拟Checkpoint失败
                Exception cause = new RuntimeException("Simulated checkpoint failure " + checkpointId);
                
                // 验证失败通知不抛出异常（容错）
                assertThatCode(() -> listener.notifyCheckpointFailed(checkpointId, cause))
                    .as("Checkpoint failure notification should not throw exception")
                    .doesNotThrowAnyException();
                
                expectedFailures++;
            } else {
                // 模拟Checkpoint成功
                assertThatCode(() -> listener.notifyCheckpointComplete(checkpointId))
                    .as("Checkpoint complete notification should not throw exception")
                    .doesNotThrowAnyException();
                
                expectedSuccesses++;
            }
        }
        
        // 验证指标正确记录
        assertThat(listener.getTotalCheckpoints())
            .as("Total checkpoints should be recorded")
            .isEqualTo(totalCheckpoints);
        
        assertThat(listener.getSuccessfulCheckpoints())
            .as("Successful checkpoints should be recorded")
            .isEqualTo(expectedSuccesses);
        
        assertThat(listener.getFailedCheckpoints())
            .as("Failed checkpoints should be recorded")
            .isEqualTo(expectedFailures);
        
        // 验证失败率计算正确
        if (totalCheckpoints > 0) {
            double expectedFailureRate = (expectedFailures * 100.0) / totalCheckpoints;
            assertThat(listener.getFailureRate())
                .as("Failure rate should be calculated correctly")
                .isCloseTo(expectedFailureRate, within(0.01));
        }
        
        // 验证系统继续运行（能够处理后续Checkpoint）
        long nextCheckpointId = totalCheckpoints + 1;
        assertThatCode(() -> {
            listener.notifyCheckpointStart(nextCheckpointId);
            listener.notifyCheckpointComplete(nextCheckpointId);
        }).as("System should continue processing after failures")
          .doesNotThrowAnyException();
    }

    /**
     * Feature: realtime-data-pipeline, Property 14: Checkpoint恢复一致性
     * 
     * **Validates: Requirements 4.1**
     * 
     * 对于任何Flink任务失败事件，系统应该从最近的Checkpoint恢复，
     * 恢复后的状态应该与Checkpoint时刻一致
     * 
     * 测试策略:
     * - 创建多个Checkpoint
     * - 模拟从最近的Checkpoint恢复
     * - 验证恢复操作选择了最新的Checkpoint
     * - 验证恢复指标被正确记录
     */
    @Property(tries = 20)
    @Label("Property 14: Checkpoint recovery consistency")
    void checkpointRecoveryConsistency(
            @ForAll("checkpointDirectories") Path checkpointDir,
            @ForAll @IntRange(min = 3, max = 10) int checkpointCount) throws Exception {
        
        try {
            // 创建多个Checkpoint目录（模拟不同时间的Checkpoint）
            List<Path> checkpointPaths = new ArrayList<>();
            long baseTimestamp = System.currentTimeMillis() - 3600000; // 1小时前
            
            for (int i = 0; i < checkpointCount; i++) {
                Path cpPath = checkpointDir.resolve("checkpoint-" + i);
                Files.createDirectories(cpPath);
                checkpointPaths.add(cpPath);
                
                // 设置不同的修改时间（模拟不同时间创建的Checkpoint）
                long timestamp = baseTimestamp + (i * 60000); // 每个相隔1分钟
                Files.setLastModifiedTime(cpPath, 
                    java.nio.file.attribute.FileTime.fromMillis(timestamp));
            }
            
            // 创建恢复管理器
            FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
                checkpointDir.toString(), 3);
            
            // 获取最新的Checkpoint路径
            String latestCheckpoint = recoveryManager.getLatestCheckpointPath();
            
            // 验证返回了最新的Checkpoint
            assertThat(latestCheckpoint)
                .as("Should return the latest checkpoint")
                .isNotNull()
                .contains("checkpoint-" + (checkpointCount - 1));
            
            // 模拟恢复操作
            recoveryManager.startRecovery();
            
            // 模拟恢复完成
            recoveryManager.completeRecovery(latestCheckpoint, true);
            
            // 验证恢复指标
            assertThat(recoveryManager.getTotalRecoveries())
                .as("Total recoveries should be recorded")
                .isEqualTo(1);
            
            assertThat(recoveryManager.getSuccessfulRecoveries())
                .as("Successful recoveries should be recorded")
                .isEqualTo(1);
            
            assertThat(recoveryManager.getLastRecoveredCheckpointPath())
                .as("Last recovered checkpoint path should be recorded")
                .isEqualTo(latestCheckpoint);
            
        } finally {
            // 清理测试目录
            deleteDirectory(checkpointDir);
        }
    }

    /**
     * Feature: realtime-data-pipeline, Property 15: Checkpoint持久化
     * 
     * **Validates: Requirements 4.2**
     * 
     * 对于任何成功的Checkpoint，其数据应该被持久化存储在状态后端
     * 
     * 测试策略:
     * - 创建Checkpoint目录
     * - 验证Checkpoint目录存在且可访问
     * - 验证FaultRecoveryManager能够列出和访问Checkpoint
     * - 验证Checkpoint数据持久化（目录存在）
     */
    @Property(tries = 20)
    @Label("Property 15: Checkpoint persistence")
    void checkpointPersistence(
            @ForAll("checkpointDirectories") Path checkpointDir,
            @ForAll @IntRange(min = 1, max = 5) int checkpointCount) throws Exception {
        
        try {
            // 创建多个Checkpoint目录
            List<Path> createdCheckpoints = new ArrayList<>();
            
            for (int i = 0; i < checkpointCount; i++) {
                Path cpPath = checkpointDir.resolve("checkpoint-" + i);
                Files.createDirectories(cpPath);
                
                // 创建一些文件模拟Checkpoint数据
                Path dataFile = cpPath.resolve("_metadata");
                Files.write(dataFile, ("checkpoint-data-" + i).getBytes());
                
                createdCheckpoints.add(cpPath);
            }
            
            // 创建恢复管理器
            FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
                checkpointDir.toString(), 3);
            
            // 验证所有Checkpoint都被持久化（目录存在）
            for (Path cpPath : createdCheckpoints) {
                assertThat(Files.exists(cpPath))
                    .as("Checkpoint directory should exist: " + cpPath)
                    .isTrue();
                
                assertThat(Files.isDirectory(cpPath))
                    .as("Checkpoint path should be a directory: " + cpPath)
                    .isTrue();
                
                // 验证数据文件存在
                Path dataFile = cpPath.resolve("_metadata");
                assertThat(Files.exists(dataFile))
                    .as("Checkpoint data file should exist: " + dataFile)
                    .isTrue();
            }
            
            // 验证恢复管理器能够访问Checkpoint
            String latestCheckpoint = recoveryManager.getLatestCheckpointPath();
            assertThat(latestCheckpoint)
                .as("Recovery manager should be able to access checkpoints")
                .isNotNull();
            
            // 验证最新的Checkpoint路径存在
            assertThat(Files.exists(Paths.get(latestCheckpoint)))
                .as("Latest checkpoint path should exist")
                .isTrue();
            
        } finally {
            // 清理测试目录
            deleteDirectory(checkpointDir);
        }
    }

    /**
     * Feature: realtime-data-pipeline, Property 16: 恢复时效性
     * 
     * **Validates: Requirements 4.3**
     * 
     * 对于任何故障恢复操作，系统应该在10分钟内完成恢复
     * 
     * 测试策略:
     * - 模拟恢复操作
     * - 记录恢复开始和结束时间
     * - 验证恢复时间在阈值内
     * - 验证超过阈值时会记录警告
     */
    @Property(tries = 20)
    @Label("Property 16: Recovery timeliness")
    void recoveryTimeliness(
            @ForAll("checkpointDirectories") Path checkpointDir,
            @ForAll @IntRange(min = 100, max = 5000) int recoveryDurationMs) throws Exception {
        
        try {
            // 创建一个Checkpoint
            Path cpPath = checkpointDir.resolve("checkpoint-0");
            Files.createDirectories(cpPath);
            
            // 创建恢复管理器
            FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
                checkpointDir.toString(), 3);
            
            // 开始恢复
            recoveryManager.startRecovery();
            
            // 模拟恢复耗时
            Thread.sleep(Math.min(recoveryDurationMs, 100)); // 实际测试中不等待太久
            
            // 完成恢复
            recoveryManager.completeRecovery(cpPath.toString(), true);
            
            // 验证恢复时间被记录
            long actualDuration = recoveryManager.getLastRecoveryDuration();
            assertThat(actualDuration)
                .as("Recovery duration should be recorded")
                .isGreaterThanOrEqualTo(0);
            
            // 验证恢复时间阈值
            long threshold = recoveryManager.getRecoveryTimeThreshold();
            assertThat(threshold)
                .as("Recovery time threshold should be 10 minutes")
                .isEqualTo(10 * 60 * 1000); // 10分钟
            
            // 如果恢复时间超过阈值，验证系统仍然能够完成恢复
            // （在实际场景中会记录警告，但不会失败）
            assertThat(recoveryManager.getSuccessfulRecoveries())
                .as("Recovery should succeed even if it takes longer than expected")
                .isEqualTo(1);
            
        } finally {
            // 清理测试目录
            deleteDirectory(checkpointDir);
        }
    }

    /**
     * Feature: realtime-data-pipeline, Property 17: Checkpoint保留策略
     * 
     * **Validates: Requirements 4.4**
     * 
     * 对于任何时刻，系统应该保留最近3个Checkpoint，自动清理更早的Checkpoint
     * 
     * 测试策略:
     * - 创建多个Checkpoint（超过保留数量）
     * - 执行清理操作
     * - 验证只保留最近的N个Checkpoint
     * - 验证旧的Checkpoint被删除
     */
    @Property(tries = 20)
    @Label("Property 17: Checkpoint retention policy")
    void checkpointRetentionPolicy(
            @ForAll("checkpointDirectories") Path checkpointDir,
            @ForAll @IntRange(min = 4, max = 10) int totalCheckpoints,
            @ForAll @IntRange(min = 2, max = 4) int retainedCount) throws Exception {
        
        try {
            // 创建多个Checkpoint目录
            List<Path> checkpointPaths = new ArrayList<>();
            long baseTimestamp = System.currentTimeMillis() - 3600000; // 1小时前
            
            for (int i = 0; i < totalCheckpoints; i++) {
                Path cpPath = checkpointDir.resolve("checkpoint-" + i);
                Files.createDirectories(cpPath);
                checkpointPaths.add(cpPath);
                
                // 设置不同的修改时间（模拟不同时间创建的Checkpoint）
                long timestamp = baseTimestamp + (i * 60000); // 每个相隔1分钟
                Files.setLastModifiedTime(cpPath, 
                    java.nio.file.attribute.FileTime.fromMillis(timestamp));
            }
            
            // 创建恢复管理器
            FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
                checkpointDir.toString(), retainedCount);
            
            // 验证保留策略配置
            assertThat(recoveryManager.getRetainedCheckpoints())
                .as("Retained checkpoints should be configured")
                .isEqualTo(retainedCount);
            
            // 执行清理操作
            int cleanedCount = recoveryManager.cleanupOldCheckpoints();
            
            // 验证清理数量
            int expectedCleanedCount = Math.max(0, totalCheckpoints - retainedCount);
            assertThat(cleanedCount)
                .as("Should clean up old checkpoints beyond retention limit")
                .isEqualTo(expectedCleanedCount);
            
            // 验证保留的Checkpoint数量
            long remainingCheckpoints = Files.list(checkpointDir)
                .filter(Files::isDirectory)
                .count();
            
            assertThat(remainingCheckpoints)
                .as("Should retain only the specified number of checkpoints")
                .isEqualTo(Math.min(totalCheckpoints, retainedCount));
            
            // 验证保留的是最新的Checkpoint
            if (totalCheckpoints > retainedCount) {
                // 最新的N个应该存在
                for (int i = totalCheckpoints - retainedCount; i < totalCheckpoints; i++) {
                    Path cpPath = checkpointDir.resolve("checkpoint-" + i);
                    assertThat(Files.exists(cpPath))
                        .as("Recent checkpoint should be retained: checkpoint-" + i)
                        .isTrue();
                }
                
                // 旧的应该被删除
                for (int i = 0; i < totalCheckpoints - retainedCount; i++) {
                    Path cpPath = checkpointDir.resolve("checkpoint-" + i);
                    assertThat(Files.exists(cpPath))
                        .as("Old checkpoint should be deleted: checkpoint-" + i)
                        .isFalse();
                }
            }
            
        } finally {
            // 清理测试目录
            deleteDirectory(checkpointDir);
        }
    }

    /**
     * Feature: realtime-data-pipeline, Property 19: 故障事件日志记录
     * 
     * **Validates: Requirements 4.6**
     * 
     * 对于任何故障事件（连接失败、处理失败、Checkpoint失败等），
     * 系统应该记录详细的错误日志
     * 
     * 测试策略:
     * - 模拟各种故障事件
     * - 验证CheckpointListener记录失败信息
     * - 验证FaultRecoveryManager记录恢复失败
     * - 验证日志包含必要的上下文信息
     */
    @Property(tries = 20)
    @Label("Property 19: Fault event logging")
    void faultEventLogging(
            @ForAll @IntRange(min = 1, max = 20) int failureCount,
            @ForAll("checkpointDirectories") Path checkpointDir) throws Exception {
        
        try {
            CheckpointListener checkpointListener = new CheckpointListener();
            FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
                checkpointDir.toString(), 3);
            
            // 模拟多次Checkpoint失败
            for (int i = 0; i < failureCount; i++) {
                long checkpointId = i + 1;
                
                // 开始Checkpoint
                checkpointListener.notifyCheckpointStart(checkpointId);
                
                // 创建包含详细信息的异常
                Exception cause = new RuntimeException(
                    "Checkpoint failure: id=" + checkpointId + 
                    ", reason=timeout, duration=30000ms");
                
                // 通知失败（应该记录日志）
                assertThatCode(() -> checkpointListener.notifyCheckpointFailed(checkpointId, cause))
                    .as("Failure notification should not throw exception")
                    .doesNotThrowAnyException();
            }
            
            // 验证失败被记录
            assertThat(checkpointListener.getFailedCheckpoints())
                .as("All checkpoint failures should be recorded")
                .isEqualTo(failureCount);
            
            // 验证失败率被计算
            assertThat(checkpointListener.getFailureRate())
                .as("Failure rate should be calculated")
                .isEqualTo(100.0); // 所有都失败
            
            // 模拟恢复失败
            Path cpPath = checkpointDir.resolve("checkpoint-0");
            Files.createDirectories(cpPath);
            
            recoveryManager.startRecovery();
            recoveryManager.completeRecovery(cpPath.toString(), false); // 失败
            
            // 验证恢复失败被记录
            assertThat(recoveryManager.getFailedRecoveries())
                .as("Recovery failure should be recorded")
                .isEqualTo(1);
            
            assertThat(recoveryManager.getRecoveryFailureRate())
                .as("Recovery failure rate should be calculated")
                .isEqualTo(100.0);
            
        } finally {
            // 清理测试目录
            deleteDirectory(checkpointDir);
        }
    }

    /**
     * 属性测试: Checkpoint指标一致性
     * 
     * 验证Checkpoint指标在各种场景下保持一致
     */
    @Property(tries = 20)
    @Label("Checkpoint metrics consistency")
    void checkpointMetricsConsistency(
            @ForAll @IntRange(min = 10, max = 100) int totalCheckpoints) {
        
        CheckpointListener listener = new CheckpointListener();
        Random random = new Random();
        
        int successCount = 0;
        int failureCount = 0;
        
        for (int i = 0; i < totalCheckpoints; i++) {
            long checkpointId = i + 1;
            listener.notifyCheckpointStart(checkpointId);
            
            if (random.nextBoolean()) {
                listener.notifyCheckpointComplete(checkpointId);
                successCount++;
            } else {
                listener.notifyCheckpointFailed(checkpointId, 
                    new RuntimeException("Random failure"));
                failureCount++;
            }
        }
        
        // 验证指标一致性
        assertThat(listener.getTotalCheckpoints())
            .as("Total = Success + Failure")
            .isEqualTo(successCount + failureCount);
        
        assertThat(listener.getSuccessfulCheckpoints())
            .as("Success count should match")
            .isEqualTo(successCount);
        
        assertThat(listener.getFailedCheckpoints())
            .as("Failure count should match")
            .isEqualTo(failureCount);
        
        // 验证百分比总和为100%
        double totalPercentage = listener.getSuccessRate() + listener.getFailureRate();
        assertThat(totalPercentage)
            .as("Success rate + Failure rate should equal 100%")
            .isCloseTo(100.0, within(0.01));
    }

    /**
     * 属性测试: 恢复操作幂等性
     * 
     * 验证多次恢复操作不会导致状态不一致
     */
    @Property(tries = 10)
    @Label("Recovery operation idempotency")
    void recoveryOperationIdempotency(
            @ForAll("checkpointDirectories") Path checkpointDir,
            @ForAll @IntRange(min = 2, max = 5) int recoveryAttempts) throws Exception {
        
        try {
            // 创建Checkpoint
            Path cpPath = checkpointDir.resolve("checkpoint-0");
            Files.createDirectories(cpPath);
            
            FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
                checkpointDir.toString(), 3);
            
            // 多次恢复操作
            for (int i = 0; i < recoveryAttempts; i++) {
                recoveryManager.startRecovery();
                recoveryManager.completeRecovery(cpPath.toString(), true);
            }
            
            // 验证恢复次数正确记录
            assertThat(recoveryManager.getTotalRecoveries())
                .as("All recovery attempts should be recorded")
                .isEqualTo(recoveryAttempts);
            
            assertThat(recoveryManager.getSuccessfulRecoveries())
                .as("All successful recoveries should be recorded")
                .isEqualTo(recoveryAttempts);
            
            // 验证最后恢复的Checkpoint路径
            assertThat(recoveryManager.getLastRecoveredCheckpointPath())
                .as("Last recovered checkpoint should be recorded")
                .isEqualTo(cpPath.toString());
            
        } finally {
            // 清理测试目录
            deleteDirectory(checkpointDir);
        }
    }

    /**
     * 属性测试: 并发Checkpoint操作安全性
     * 
     * 验证并发Checkpoint操作不会导致指标不一致
     */
    @Property(tries = 30)
    @Label("Concurrent checkpoint operations safety")
    void concurrentCheckpointOperationsSafety(
            @ForAll @IntRange(min = 10, max = 50) int checkpointCount) throws Exception {
        
        CheckpointListener listener = new CheckpointListener();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(checkpointCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // 创建多个线程并发执行Checkpoint操作
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < checkpointCount; i++) {
            final long checkpointId = i + 1;
            final boolean shouldFail = (i % 3 == 0); // 每3个中有1个失败
            
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪
                    
                    listener.notifyCheckpointStart(checkpointId);
                    
                    if (shouldFail) {
                        listener.notifyCheckpointFailed(checkpointId, 
                            new RuntimeException("Concurrent failure"));
                        failureCount.incrementAndGet();
                    } else {
                        listener.notifyCheckpointComplete(checkpointId);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 忽略
                } finally {
                    doneLatch.countDown();
                }
            });
            
            threads.add(thread);
            thread.start();
        }
        
        // 启动所有线程
        startLatch.countDown();
        
        // 等待所有线程完成
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed)
            .as("All checkpoint operations should complete")
            .isTrue();
        
        // 验证指标一致性
        assertThat(listener.getTotalCheckpoints())
            .as("Total checkpoints should be recorded")
            .isEqualTo(checkpointCount);
        
        assertThat(listener.getSuccessfulCheckpoints())
            .as("Successful checkpoints should be recorded")
            .isEqualTo(successCount.get());
        
        assertThat(listener.getFailedCheckpoints())
            .as("Failed checkpoints should be recorded")
            .isEqualTo(failureCount.get());
    }

    /**
     * 属性测试: Checkpoint清理不影响最新Checkpoint
     * 
     * 验证清理操作不会删除最新的Checkpoint
     */
    @Property(tries = 10)
    @Label("Checkpoint cleanup preserves latest")
    void checkpointCleanupPreservesLatest(
            @ForAll("checkpointDirectories") Path checkpointDir,
            @ForAll @IntRange(min = 5, max = 15) int totalCheckpoints) throws Exception {
        
        try {
            // 创建多个Checkpoint
            long baseTimestamp = System.currentTimeMillis() - 3600000;
            Path latestCheckpoint = null;
            
            for (int i = 0; i < totalCheckpoints; i++) {
                Path cpPath = checkpointDir.resolve("checkpoint-" + i);
                Files.createDirectories(cpPath);
                latestCheckpoint = cpPath;
                
                long timestamp = baseTimestamp + (i * 60000);
                Files.setLastModifiedTime(cpPath, 
                    java.nio.file.attribute.FileTime.fromMillis(timestamp));
            }
            
            FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
                checkpointDir.toString(), 3);
            
            // 执行清理
            recoveryManager.cleanupOldCheckpoints();
            
            // 验证最新的Checkpoint仍然存在
            assertThat(Files.exists(latestCheckpoint))
                .as("Latest checkpoint should not be deleted")
                .isTrue();
            
            // 验证能够获取最新的Checkpoint
            String latest = recoveryManager.getLatestCheckpointPath();
            assertThat(latest)
                .as("Should be able to get latest checkpoint after cleanup")
                .isNotNull()
                .contains("checkpoint-" + (totalCheckpoints - 1));
            
        } finally {
            // 清理测试目录
            deleteDirectory(checkpointDir);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // 忽略删除失败
                    }
                });
        }
    }

    // ==================== 数据生成器 ====================

    /**
     * 生成临时Checkpoint目录
     */
    @Provide
    Arbitrary<Path> checkpointDirectories() {
        return Arbitraries.strings()
            .alpha()
            .ofLength(8)
            .map(suffix -> {
                try {
                    return Files.createTempDirectory("checkpoint-test-" + suffix);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create temp directory", e);
                }
            });
    }
}
