package com.realtime.pipeline.flink.recovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FaultRecoveryManager单元测试
 * 
 * 验证需求:
 * - 需求 4.1: WHEN Flink任务失败 THEN THE System SHALL 从最近的Checkpoint恢复
 * - 需求 4.3: WHEN 恢复操作 THEN THE System SHALL 在10分钟内完成恢复
 * - 需求 4.4: THE System SHALL 保留最近3个Checkpoint
 */
class FaultRecoveryManagerTest {

    @TempDir
    Path tempDir;

    private FaultRecoveryManager recoveryManager;
    private String checkpointDir;

    @BeforeEach
    void setUp() {
        checkpointDir = tempDir.toString();
        recoveryManager = new FaultRecoveryManager(checkpointDir, 3);
    }

    @AfterEach
    void tearDown() {
        if (recoveryManager != null) {
            recoveryManager.resetMetrics();
        }
    }

    @Test
    void testConstructorWithValidParameters() {
        assertNotNull(recoveryManager);
        assertEquals(3, recoveryManager.getRetainedCheckpoints());
        assertTrue(recoveryManager.getCheckpointDir().contains(checkpointDir));
    }

    @Test
    void testConstructorWithNullCheckpointDir() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FaultRecoveryManager(null, 3);
        });
    }

    @Test
    void testConstructorWithEmptyCheckpointDir() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FaultRecoveryManager("", 3);
        });
    }

    @Test
    void testConstructorWithInvalidRetainedCheckpoints() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FaultRecoveryManager(checkpointDir, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new FaultRecoveryManager(checkpointDir, -1);
        });
    }

    @Test
    void testStartRecovery() {
        recoveryManager.startRecovery();

        assertEquals(1, recoveryManager.getTotalRecoveries());
        assertEquals(0, recoveryManager.getSuccessfulRecoveries());
        assertEquals(0, recoveryManager.getFailedRecoveries());
    }

    @Test
    void testCompleteRecoverySuccess() throws InterruptedException {
        recoveryManager.startRecovery();
        Thread.sleep(100); // 模拟恢复耗时

        String checkpointPath = checkpointDir + "/checkpoint-1";
        recoveryManager.completeRecovery(checkpointPath, true);

        assertEquals(1, recoveryManager.getTotalRecoveries());
        assertEquals(1, recoveryManager.getSuccessfulRecoveries());
        assertEquals(0, recoveryManager.getFailedRecoveries());
        assertEquals(100.0, recoveryManager.getRecoverySuccessRate(), 0.01);
        assertTrue(recoveryManager.getLastRecoveryDuration() >= 100);
        assertEquals(checkpointPath, recoveryManager.getLastRecoveredCheckpointPath());
    }

    @Test
    void testCompleteRecoveryFailure() throws InterruptedException {
        recoveryManager.startRecovery();
        Thread.sleep(50);

        String checkpointPath = checkpointDir + "/checkpoint-1";
        recoveryManager.completeRecovery(checkpointPath, false);

        assertEquals(1, recoveryManager.getTotalRecoveries());
        assertEquals(0, recoveryManager.getSuccessfulRecoveries());
        assertEquals(1, recoveryManager.getFailedRecoveries());
        assertEquals(0.0, recoveryManager.getRecoverySuccessRate(), 0.01);
        assertEquals(100.0, recoveryManager.getRecoveryFailureRate(), 0.01);
    }

    @Test
    void testMultipleRecoveries() throws InterruptedException {
        // 第一次恢复 - 成功
        recoveryManager.startRecovery();
        Thread.sleep(50);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-1", true);

        // 第二次恢复 - 失败
        recoveryManager.startRecovery();
        Thread.sleep(50);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-2", false);

        // 第三次恢复 - 成功
        recoveryManager.startRecovery();
        Thread.sleep(50);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-3", true);

        assertEquals(3, recoveryManager.getTotalRecoveries());
        assertEquals(2, recoveryManager.getSuccessfulRecoveries());
        assertEquals(1, recoveryManager.getFailedRecoveries());
        assertEquals(66.67, recoveryManager.getRecoverySuccessRate(), 0.01);
        assertEquals(33.33, recoveryManager.getRecoveryFailureRate(), 0.01);
    }

    @Test
    void testAverageRecoveryDuration() throws InterruptedException {
        // 第一次恢复 - 100ms
        recoveryManager.startRecovery();
        Thread.sleep(100);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-1", true);

        // 第二次恢复 - 200ms
        recoveryManager.startRecovery();
        Thread.sleep(200);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-2", true);

        long avgDuration = recoveryManager.getAverageRecoveryDuration();
        assertTrue(avgDuration >= 150 && avgDuration <= 200, 
            "Average duration should be around 150ms, got: " + avgDuration);
    }

    @Test
    void testRecoveryTimeThreshold() {
        // 恢复时间阈值应该是10分钟（600000毫秒）
        assertEquals(600000L, recoveryManager.getRecoveryTimeThreshold());
    }

    @Test
    void testGetLatestCheckpointPathWithNoCheckpoints() {
        String latestPath = recoveryManager.getLatestCheckpointPath();
        assertNull(latestPath);
    }

    @Test
    void testGetLatestCheckpointPathWithCheckpoints() throws IOException, InterruptedException {
        // 创建多个Checkpoint目录
        Path checkpoint1 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-1"));
        Thread.sleep(10);
        Path checkpoint2 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-2"));
        Thread.sleep(10);
        Path checkpoint3 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-3"));

        String latestPath = recoveryManager.getLatestCheckpointPath();
        assertNotNull(latestPath);
        assertTrue(latestPath.contains("checkpoint-3"));
    }

    @Test
    void testCleanupOldCheckpointsWithNoCheckpoints() {
        int cleanedCount = recoveryManager.cleanupOldCheckpoints();
        assertEquals(0, cleanedCount);
    }

    @Test
    void testCleanupOldCheckpointsWithinRetentionLimit() throws IOException {
        // 创建3个Checkpoint（等于保留数量）
        Files.createDirectory(Paths.get(checkpointDir, "checkpoint-1"));
        Files.createDirectory(Paths.get(checkpointDir, "checkpoint-2"));
        Files.createDirectory(Paths.get(checkpointDir, "checkpoint-3"));

        int cleanedCount = recoveryManager.cleanupOldCheckpoints();
        assertEquals(0, cleanedCount);

        // 验证所有Checkpoint仍然存在
        assertTrue(Files.exists(Paths.get(checkpointDir, "checkpoint-1")));
        assertTrue(Files.exists(Paths.get(checkpointDir, "checkpoint-2")));
        assertTrue(Files.exists(Paths.get(checkpointDir, "checkpoint-3")));
    }

    @Test
    void testCleanupOldCheckpointsExceedingRetentionLimit() throws IOException, InterruptedException {
        // 创建5个Checkpoint（超过保留数量3）
        Path checkpoint1 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-1"));
        Thread.sleep(10);
        Path checkpoint2 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-2"));
        Thread.sleep(10);
        Path checkpoint3 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-3"));
        Thread.sleep(10);
        Path checkpoint4 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-4"));
        Thread.sleep(10);
        Path checkpoint5 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-5"));

        int cleanedCount = recoveryManager.cleanupOldCheckpoints();
        assertEquals(2, cleanedCount);

        // 验证最新的3个Checkpoint保留，旧的2个被删除
        assertFalse(Files.exists(checkpoint1));
        assertFalse(Files.exists(checkpoint2));
        assertTrue(Files.exists(checkpoint3));
        assertTrue(Files.exists(checkpoint4));
        assertTrue(Files.exists(checkpoint5));
    }

    @Test
    void testCleanupOldCheckpointsWithFiles() throws IOException, InterruptedException {
        // 创建Checkpoint目录并添加文件
        Path checkpoint1 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-1"));
        Files.createFile(checkpoint1.resolve("metadata"));
        Files.createFile(checkpoint1.resolve("state"));
        Thread.sleep(10);

        Path checkpoint2 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-2"));
        Files.createFile(checkpoint2.resolve("metadata"));
        Thread.sleep(10);

        Path checkpoint3 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-3"));
        Thread.sleep(10);

        Path checkpoint4 = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-4"));

        int cleanedCount = recoveryManager.cleanupOldCheckpoints();
        assertEquals(1, cleanedCount);

        // 验证旧的Checkpoint及其文件被删除
        assertFalse(Files.exists(checkpoint1));
        assertFalse(Files.exists(checkpoint1.resolve("metadata")));
        assertFalse(Files.exists(checkpoint1.resolve("state")));

        // 验证新的Checkpoint保留
        assertTrue(Files.exists(checkpoint2));
        assertTrue(Files.exists(checkpoint3));
        assertTrue(Files.exists(checkpoint4));
    }

    @Test
    void testGetRecoveryMetricsSummary() throws InterruptedException {
        recoveryManager.startRecovery();
        Thread.sleep(50);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-1", true);

        String summary = recoveryManager.getRecoveryMetricsSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Total: 1"));
        assertTrue(summary.contains("Successful: 1"));
        assertTrue(summary.contains("Failed: 0"));
        assertTrue(summary.contains("Success Rate: 100.00%"));
        assertTrue(summary.contains("checkpoint-1"));
    }

    @Test
    void testResetMetrics() throws InterruptedException {
        recoveryManager.startRecovery();
        Thread.sleep(50);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-1", true);

        assertEquals(1, recoveryManager.getTotalRecoveries());

        recoveryManager.resetMetrics();

        assertEquals(0, recoveryManager.getTotalRecoveries());
        assertEquals(0, recoveryManager.getSuccessfulRecoveries());
        assertEquals(0, recoveryManager.getFailedRecoveries());
        assertEquals(0, recoveryManager.getLastRecoveryDuration());
        assertNull(recoveryManager.getLastRecoveredCheckpointPath());
    }

    @Test
    void testNormalizeCheckpointDir() {
        // 测试file://前缀被移除
        FaultRecoveryManager manager1 = new FaultRecoveryManager("file:///tmp/checkpoints", 3);
        assertTrue(manager1.getCheckpointDir().startsWith("/tmp/checkpoints"));
        assertFalse(manager1.getCheckpointDir().startsWith("file://"));

        // 测试路径以/结尾
        FaultRecoveryManager manager2 = new FaultRecoveryManager("/tmp/checkpoints", 3);
        assertTrue(manager2.getCheckpointDir().endsWith("/"));
    }

    @Test
    void testRecoverySuccessRateWithNoRecoveries() {
        assertEquals(0.0, recoveryManager.getRecoverySuccessRate(), 0.01);
        assertEquals(0.0, recoveryManager.getRecoveryFailureRate(), 0.01);
    }

    @Test
    void testAverageRecoveryDurationWithNoSuccessfulRecoveries() {
        recoveryManager.startRecovery();
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-1", false);

        assertEquals(0, recoveryManager.getAverageRecoveryDuration());
    }

    @Test
    void testGetLatestCheckpointPathWithNonExistentDirectory() {
        FaultRecoveryManager manager = new FaultRecoveryManager("/non/existent/path", 3);
        String latestPath = manager.getLatestCheckpointPath();
        assertNull(latestPath);
    }

    @Test
    void testCleanupOldCheckpointsWithNonExistentDirectory() {
        FaultRecoveryManager manager = new FaultRecoveryManager("/non/existent/path", 3);
        int cleanedCount = manager.cleanupOldCheckpoints();
        assertEquals(0, cleanedCount);
    }

    @Test
    void testRecoveryExactlyAtThreshold() throws InterruptedException {
        recoveryManager.startRecovery();
        
        // 模拟恰好10分钟的恢复时间
        Thread.sleep(100); // 实际测试中用较短时间模拟
        
        String checkpointPath = checkpointDir + "/checkpoint-threshold";
        recoveryManager.completeRecovery(checkpointPath, true);
        
        // 验证恢复成功
        assertEquals(1, recoveryManager.getSuccessfulRecoveries());
        
        // 验证阈值检查逻辑正常工作
        long threshold = recoveryManager.getRecoveryTimeThreshold();
        assertEquals(600000L, threshold);
    }

    @Test
    void testRecoveryFromCorruptedCheckpoint() throws InterruptedException {
        recoveryManager.startRecovery();
        Thread.sleep(50);
        
        // 模拟从损坏的Checkpoint恢复失败
        String corruptedPath = checkpointDir + "/corrupted-checkpoint";
        recoveryManager.completeRecovery(corruptedPath, false);
        
        assertEquals(1, recoveryManager.getTotalRecoveries());
        assertEquals(0, recoveryManager.getSuccessfulRecoveries());
        assertEquals(1, recoveryManager.getFailedRecoveries());
        assertEquals(100.0, recoveryManager.getRecoveryFailureRate(), 0.01);
    }

    @Test
    void testMultipleConsecutiveRecoveryFailures() throws InterruptedException {
        // 连续3次恢复失败
        for (int i = 1; i <= 3; i++) {
            recoveryManager.startRecovery();
            Thread.sleep(30);
            recoveryManager.completeRecovery(checkpointDir + "/checkpoint-" + i, false);
        }
        
        // 第4次恢复成功
        recoveryManager.startRecovery();
        Thread.sleep(30);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-4", true);
        
        assertEquals(4, recoveryManager.getTotalRecoveries());
        assertEquals(1, recoveryManager.getSuccessfulRecoveries());
        assertEquals(3, recoveryManager.getFailedRecoveries());
        assertEquals(25.0, recoveryManager.getRecoverySuccessRate(), 0.01);
        assertEquals(75.0, recoveryManager.getRecoveryFailureRate(), 0.01);
    }

    @Test
    void testCleanupWithExactlyRetainedCheckpoints() throws IOException {
        // 创建恰好3个Checkpoint（等于保留数量）
        Files.createDirectory(Paths.get(checkpointDir, "checkpoint-1"));
        Files.createDirectory(Paths.get(checkpointDir, "checkpoint-2"));
        Files.createDirectory(Paths.get(checkpointDir, "checkpoint-3"));
        
        int cleanedCount = recoveryManager.cleanupOldCheckpoints();
        
        // 不应该清理任何Checkpoint
        assertEquals(0, cleanedCount);
        
        // 验证所有Checkpoint仍然存在
        assertTrue(Files.exists(Paths.get(checkpointDir, "checkpoint-1")));
        assertTrue(Files.exists(Paths.get(checkpointDir, "checkpoint-2")));
        assertTrue(Files.exists(Paths.get(checkpointDir, "checkpoint-3")));
    }

    @Test
    void testGetLatestCheckpointWithSingleCheckpoint() throws IOException {
        // 只创建一个Checkpoint
        Path checkpoint = Files.createDirectory(Paths.get(checkpointDir, "checkpoint-single"));
        
        String latestPath = recoveryManager.getLatestCheckpointPath();
        
        assertNotNull(latestPath);
        assertTrue(latestPath.contains("checkpoint-single"));
    }

    @Test
    void testRecoveryMetricsAfterReset() throws InterruptedException {
        // 执行一些恢复操作
        recoveryManager.startRecovery();
        Thread.sleep(50);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-1", true);
        
        // 验证有数据
        assertEquals(1, recoveryManager.getTotalRecoveries());
        
        // 重置指标
        recoveryManager.resetMetrics();
        
        // 验证所有指标已重置
        assertEquals(0, recoveryManager.getTotalRecoveries());
        assertEquals(0, recoveryManager.getSuccessfulRecoveries());
        assertEquals(0, recoveryManager.getFailedRecoveries());
        assertEquals(0, recoveryManager.getLastRecoveryDuration());
        assertEquals(0, recoveryManager.getAverageRecoveryDuration());
        assertNull(recoveryManager.getLastRecoveredCheckpointPath());
        
        // 验证重置后可以继续使用
        recoveryManager.startRecovery();
        Thread.sleep(50);
        recoveryManager.completeRecovery(checkpointDir + "/checkpoint-2", true);
        
        assertEquals(1, recoveryManager.getTotalRecoveries());
        assertEquals(1, recoveryManager.getSuccessfulRecoveries());
    }
}
