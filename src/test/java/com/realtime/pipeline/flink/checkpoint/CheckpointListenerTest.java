package com.realtime.pipeline.flink.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CheckpointListener单元测试
 * 
 * 验证需求:
 * - 需求 2.5: WHEN Checkpoint失败 THEN THE Flink SHALL 记录错误日志并继续处理
 * - 需求 4.6: THE System SHALL 记录所有故障事件到日志系统
 */
class CheckpointListenerTest {

    private CheckpointListener listener;

    @BeforeEach
    void setUp() {
        listener = new CheckpointListener();
    }

    @Test
    @DisplayName("测试Checkpoint成功完成 - 记录成功指标")
    void testCheckpointComplete() {
        // 模拟Checkpoint开始
        listener.notifyCheckpointStart(1L);
        
        // 等待一小段时间模拟Checkpoint过程
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 模拟Checkpoint完成
        listener.notifyCheckpointComplete(1L);
        
        // 验证指标
        assertEquals(1, listener.getTotalCheckpoints());
        assertEquals(1, listener.getSuccessfulCheckpoints());
        assertEquals(0, listener.getFailedCheckpoints());
        assertEquals(100.0, listener.getSuccessRate(), 0.1);
        assertEquals(0.0, listener.getFailureRate(), 0.1);
        assertTrue(listener.getLastCheckpointDuration() >= 100);
        assertEquals(1L, listener.getLastCheckpointId());
    }

    @Test
    @DisplayName("测试Checkpoint失败 - 记录失败指标和错误日志")
    void testCheckpointFailed() {
        // 模拟Checkpoint开始
        listener.notifyCheckpointStart(1L);
        
        // 等待一小段时间
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 模拟Checkpoint失败
        Exception cause = new RuntimeException("Checkpoint timeout");
        listener.notifyCheckpointFailed(1L, cause);
        
        // 验证指标
        assertEquals(1, listener.getTotalCheckpoints());
        assertEquals(0, listener.getSuccessfulCheckpoints());
        assertEquals(1, listener.getFailedCheckpoints());
        assertEquals(0.0, listener.getSuccessRate(), 0.1);
        assertEquals(100.0, listener.getFailureRate(), 0.1);
    }

    @Test
    @DisplayName("测试Checkpoint中止 - 记录为失败")
    void testCheckpointAborted() {
        // 模拟Checkpoint开始
        listener.notifyCheckpointStart(1L);
        
        // 模拟Checkpoint中止
        listener.notifyCheckpointAborted(1L, "Newer checkpoint triggered");
        
        // 验证指标
        assertEquals(1, listener.getTotalCheckpoints());
        assertEquals(0, listener.getSuccessfulCheckpoints());
        assertEquals(1, listener.getFailedCheckpoints());
        assertEquals(100.0, listener.getFailureRate(), 0.1);
    }

    @Test
    @DisplayName("测试多次Checkpoint - 计算成功率")
    void testMultipleCheckpoints() {
        // 执行5次成功的Checkpoint
        for (int i = 1; i <= 5; i++) {
            listener.notifyCheckpointStart(i);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            listener.notifyCheckpointComplete(i);
        }
        
        // 执行2次失败的Checkpoint
        for (int i = 6; i <= 7; i++) {
            listener.notifyCheckpointStart(i);
            listener.notifyCheckpointFailed(i, new RuntimeException("Test failure"));
        }
        
        // 验证指标
        assertEquals(7, listener.getTotalCheckpoints());
        assertEquals(5, listener.getSuccessfulCheckpoints());
        assertEquals(2, listener.getFailedCheckpoints());
        assertEquals(71.43, listener.getSuccessRate(), 0.1);
        assertEquals(28.57, listener.getFailureRate(), 0.1);
    }

    @Test
    @DisplayName("测试平均Checkpoint耗时计算")
    void testAverageCheckpointDuration() {
        // 执行3次Checkpoint，每次耗时不同
        for (int i = 1; i <= 3; i++) {
            listener.notifyCheckpointStart(i);
            try {
                Thread.sleep(i * 50); // 50ms, 100ms, 150ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            listener.notifyCheckpointComplete(i);
        }
        
        // 验证平均耗时
        long avgDuration = listener.getAverageCheckpointDuration();
        assertTrue(avgDuration >= 90 && avgDuration <= 110, 
            "Average duration should be around 100ms, but was " + avgDuration);
    }

    @Test
    @DisplayName("测试Checkpoint失败率超过10%的警告")
    void testHighFailureRateWarning() {
        // 执行8次成功，2次失败（失败率20%）
        for (int i = 1; i <= 8; i++) {
            listener.notifyCheckpointStart(i);
            listener.notifyCheckpointComplete(i);
        }
        
        for (int i = 9; i <= 10; i++) {
            listener.notifyCheckpointStart(i);
            listener.notifyCheckpointFailed(i, new RuntimeException("Test failure"));
        }
        
        // 验证失败率
        double failureRate = listener.getFailureRate();
        assertEquals(20.0, failureRate, 0.1);
        assertTrue(failureRate > 10.0, "Failure rate should exceed 10% threshold");
    }

    @Test
    @DisplayName("测试Checkpoint失败时继续处理 - 不抛出异常")
    void testContinueProcessingOnFailure() {
        // 模拟Checkpoint失败
        listener.notifyCheckpointStart(1L);
        
        // 验证不抛出异常
        assertDoesNotThrow(() -> {
            listener.notifyCheckpointFailed(1L, new RuntimeException("Test failure"));
        });
        
        // 验证可以继续执行下一个Checkpoint
        listener.notifyCheckpointStart(2L);
        listener.notifyCheckpointComplete(2L);
        
        assertEquals(2, listener.getTotalCheckpoints());
        assertEquals(1, listener.getSuccessfulCheckpoints());
        assertEquals(1, listener.getFailedCheckpoints());
    }

    @Test
    @DisplayName("测试指标摘要字符串生成")
    void testMetricsSummary() {
        // 执行一些Checkpoint
        listener.notifyCheckpointStart(1L);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listener.notifyCheckpointComplete(1L);
        
        listener.notifyCheckpointStart(2L);
        listener.notifyCheckpointFailed(2L, new RuntimeException("Test"));
        
        // 获取摘要
        String summary = listener.getMetricsSummary();
        
        // 验证摘要包含关键信息
        assertNotNull(summary);
        assertTrue(summary.contains("Total: 2"));
        assertTrue(summary.contains("Successful: 1"));
        assertTrue(summary.contains("Failed: 1"));
        assertTrue(summary.contains("Success Rate: 50"));
    }

    @Test
    @DisplayName("测试重置指标")
    void testResetMetrics() {
        // 执行一些Checkpoint
        listener.notifyCheckpointStart(1L);
        listener.notifyCheckpointComplete(1L);
        
        listener.notifyCheckpointStart(2L);
        listener.notifyCheckpointFailed(2L, new RuntimeException("Test"));
        
        // 验证有数据
        assertEquals(2, listener.getTotalCheckpoints());
        
        // 重置指标
        listener.resetMetrics();
        
        // 验证所有指标已重置
        assertEquals(0, listener.getTotalCheckpoints());
        assertEquals(0, listener.getSuccessfulCheckpoints());
        assertEquals(0, listener.getFailedCheckpoints());
        assertEquals(0, listener.getLastCheckpointDuration());
        assertEquals(0, listener.getAverageCheckpointDuration());
        assertEquals(-1L, listener.getLastCheckpointId());
    }

    @Test
    @DisplayName("测试空Checkpoint列表的成功率")
    void testSuccessRateWithNoCheckpoints() {
        // 没有执行任何Checkpoint
        assertEquals(0.0, listener.getSuccessRate(), 0.1);
        assertEquals(0.0, listener.getFailureRate(), 0.1);
        assertEquals(0, listener.getAverageCheckpointDuration());
    }

    @Test
    @DisplayName("测试Checkpoint失败时记录null原因")
    void testCheckpointFailedWithNullCause() {
        listener.notifyCheckpointStart(1L);
        
        // 验证null原因不会导致异常
        assertDoesNotThrow(() -> {
            listener.notifyCheckpointFailed(1L, null);
        });
        
        assertEquals(1, listener.getFailedCheckpoints());
    }

    @Test
    @DisplayName("测试Checkpoint中止时记录null原因")
    void testCheckpointAbortedWithNullReason() {
        listener.notifyCheckpointStart(1L);
        
        // 验证null原因不会导致异常
        assertDoesNotThrow(() -> {
            listener.notifyCheckpointAborted(1L, null);
        });
        
        assertEquals(1, listener.getFailedCheckpoints());
    }

    @Test
    @DisplayName("测试并发Checkpoint指标更新")
    void testConcurrentCheckpointMetrics() throws InterruptedException {
        // 创建多个线程同时更新指标
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            final int checkpointId = i + 1;
            threads[i] = new Thread(() -> {
                listener.notifyCheckpointStart(checkpointId);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (checkpointId % 2 == 0) {
                    listener.notifyCheckpointComplete(checkpointId);
                } else {
                    listener.notifyCheckpointFailed(checkpointId, new RuntimeException("Test"));
                }
            });
            threads[i].start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证总数正确
        assertEquals(10, listener.getTotalCheckpoints());
        assertEquals(5, listener.getSuccessfulCheckpoints());
        assertEquals(5, listener.getFailedCheckpoints());
        assertEquals(50.0, listener.getSuccessRate(), 0.1);
    }

    @Test
    @DisplayName("测试最后一次Checkpoint耗时记录")
    void testLastCheckpointDuration() {
        // 执行第一个Checkpoint
        listener.notifyCheckpointStart(1L);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listener.notifyCheckpointComplete(1L);
        long firstDuration = listener.getLastCheckpointDuration();
        
        // 执行第二个Checkpoint，耗时更长
        listener.notifyCheckpointStart(2L);
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listener.notifyCheckpointComplete(2L);
        long secondDuration = listener.getLastCheckpointDuration();
        
        // 验证最后一次耗时被更新
        assertTrue(secondDuration > firstDuration);
        assertTrue(secondDuration >= 150);
    }

    @Test
    @DisplayName("测试Checkpoint失败场景 - 不同异常类型")
    void testCheckpointFailureWithDifferentExceptionTypes() {
        // 测试超时异常
        listener.notifyCheckpointStart(1L);
        listener.notifyCheckpointFailed(1L, new java.util.concurrent.TimeoutException("Checkpoint timeout"));
        assertEquals(1, listener.getFailedCheckpoints());
        
        // 测试IO异常
        listener.notifyCheckpointStart(2L);
        listener.notifyCheckpointFailed(2L, new java.io.IOException("Failed to write checkpoint"));
        assertEquals(2, listener.getFailedCheckpoints());
        
        // 测试运行时异常
        listener.notifyCheckpointStart(3L);
        listener.notifyCheckpointFailed(3L, new RuntimeException("Unexpected error"));
        assertEquals(3, listener.getFailedCheckpoints());
        
        // 验证所有失败都被正确记录
        assertEquals(3, listener.getTotalCheckpoints());
        assertEquals(100.0, listener.getFailureRate(), 0.1);
    }

    @Test
    @DisplayName("测试Checkpoint失败后立即成功 - 验证恢复能力")
    void testCheckpointRecoveryAfterFailure() {
        // 第一个Checkpoint失败
        listener.notifyCheckpointStart(1L);
        listener.notifyCheckpointFailed(1L, new RuntimeException("Temporary failure"));
        
        // 第二个Checkpoint立即成功
        listener.notifyCheckpointStart(2L);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listener.notifyCheckpointComplete(2L);
        
        // 验证系统从失败中恢复
        assertEquals(2, listener.getTotalCheckpoints());
        assertEquals(1, listener.getSuccessfulCheckpoints());
        assertEquals(1, listener.getFailedCheckpoints());
        assertEquals(50.0, listener.getSuccessRate(), 0.1);
    }

    @Test
    @DisplayName("测试连续Checkpoint失败 - 验证持续记录")
    void testConsecutiveCheckpointFailures() {
        // 连续5次失败
        for (int i = 1; i <= 5; i++) {
            listener.notifyCheckpointStart(i);
            listener.notifyCheckpointFailed(i, new RuntimeException("Failure " + i));
        }
        
        // 验证所有失败都被记录
        assertEquals(5, listener.getTotalCheckpoints());
        assertEquals(0, listener.getSuccessfulCheckpoints());
        assertEquals(5, listener.getFailedCheckpoints());
        assertEquals(0.0, listener.getSuccessRate(), 0.1);
        assertEquals(100.0, listener.getFailureRate(), 0.1);
    }
}
