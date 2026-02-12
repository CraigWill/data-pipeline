package com.realtime.pipeline.flink.checkpoint;

import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MetricsCheckpointListener单元测试
 * 
 * 验证需求:
 * - 需求 2.5: WHEN Checkpoint失败 THEN THE Flink SHALL 记录错误日志并继续处理
 * - 需求 4.6: THE System SHALL 记录所有故障事件到日志系统
 */
class MetricsCheckpointListenerTest {

    private MetricsCheckpointListener<String> listener;
    private CheckpointListener checkpointListener;

    @Mock
    private FunctionSnapshotContext snapshotContext;

    @Mock
    private FunctionInitializationContext initContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        checkpointListener = new CheckpointListener();
        listener = new MetricsCheckpointListener<>(checkpointListener);
    }

    @Test
    @DisplayName("测试构造函数 - null检查")
    void testConstructorNullCheck() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MetricsCheckpointListener<String>(null);
        });
    }

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        MetricsCheckpointListener<String> defaultListener = new MetricsCheckpointListener<>();
        assertNotNull(defaultListener.getCheckpointListener());
    }

    @Test
    @DisplayName("测试map函数 - 直接返回输入值")
    void testMapFunction() throws Exception {
        String input = "test-data";
        String output = listener.map(input);
        
        assertEquals(input, output);
        assertSame(input, output);
    }

    @Test
    @DisplayName("测试snapshotState - 通知Checkpoint开始")
    void testSnapshotState() throws Exception {
        when(snapshotContext.getCheckpointId()).thenReturn(1L);
        
        listener.snapshotState(snapshotContext);
        
        assertEquals(1, checkpointListener.getTotalCheckpoints());
        assertEquals(1L, checkpointListener.getLastCheckpointId());
    }

    @Test
    @DisplayName("测试initializeState - 首次初始化")
    void testInitializeStateFirstTime() throws Exception {
        when(initContext.isRestored()).thenReturn(false);
        
        assertDoesNotThrow(() -> {
            listener.initializeState(initContext);
        });
        
        verify(initContext).isRestored();
    }

    @Test
    @DisplayName("测试initializeState - 从Checkpoint恢复")
    void testInitializeStateRestored() throws Exception {
        when(initContext.isRestored()).thenReturn(true);
        
        assertDoesNotThrow(() -> {
            listener.initializeState(initContext);
        });
        
        verify(initContext).isRestored();
    }

    @Test
    @DisplayName("测试notifyCheckpointComplete - 记录成功")
    void testNotifyCheckpointComplete() throws Exception {
        // 先开始Checkpoint
        when(snapshotContext.getCheckpointId()).thenReturn(1L);
        listener.snapshotState(snapshotContext);
        
        // 等待一小段时间
        Thread.sleep(50);
        
        // 通知完成
        listener.notifyCheckpointComplete(1L);
        
        assertEquals(1, checkpointListener.getSuccessfulCheckpoints());
        assertEquals(100.0, checkpointListener.getSuccessRate(), 0.1);
    }

    @Test
    @DisplayName("测试notifyCheckpointAborted - 记录失败")
    void testNotifyCheckpointAborted() throws Exception {
        // 先开始Checkpoint
        when(snapshotContext.getCheckpointId()).thenReturn(1L);
        listener.snapshotState(snapshotContext);
        
        // 通知中止
        listener.notifyCheckpointAborted(1L);
        
        assertEquals(1, checkpointListener.getFailedCheckpoints());
        assertEquals(100.0, checkpointListener.getFailureRate(), 0.1);
    }

    @Test
    @DisplayName("测试多次Checkpoint完成 - 定期输出指标摘要")
    void testMultipleCheckpointsWithSummary() throws Exception {
        // 执行10次Checkpoint（第10次会输出摘要）
        for (int i = 1; i <= 10; i++) {
            when(snapshotContext.getCheckpointId()).thenReturn((long) i);
            listener.snapshotState(snapshotContext);
            Thread.sleep(10);
            listener.notifyCheckpointComplete((long) i);
        }
        
        assertEquals(10, checkpointListener.getTotalCheckpoints());
        assertEquals(10, checkpointListener.getSuccessfulCheckpoints());
        assertEquals(100.0, checkpointListener.getSuccessRate(), 0.1);
    }

    @Test
    @DisplayName("测试高失败率告警 - 超过10%")
    void testHighFailureRateAlert() throws Exception {
        // 执行8次成功，2次失败（失败率20%）
        for (int i = 1; i <= 8; i++) {
            when(snapshotContext.getCheckpointId()).thenReturn((long) i);
            listener.snapshotState(snapshotContext);
            listener.notifyCheckpointComplete((long) i);
        }
        
        for (int i = 9; i <= 10; i++) {
            when(snapshotContext.getCheckpointId()).thenReturn((long) i);
            listener.snapshotState(snapshotContext);
            listener.notifyCheckpointAborted((long) i);
        }
        
        double failureRate = checkpointListener.getFailureRate();
        assertEquals(20.0, failureRate, 0.1);
        assertTrue(failureRate > 10.0);
    }

    @Test
    @DisplayName("测试snapshotState异常处理 - 不影响Checkpoint")
    void testSnapshotStateExceptionHandling() throws Exception {
        // 使用一个会抛出异常的CheckpointListener
        CheckpointListener faultyListener = new CheckpointListener() {
            @Override
            public void notifyCheckpointStart(long checkpointId) {
                throw new RuntimeException("Test exception");
            }
        };
        
        MetricsCheckpointListener<String> faultyMetricsListener = 
            new MetricsCheckpointListener<>(faultyListener);
        
        when(snapshotContext.getCheckpointId()).thenReturn(1L);
        
        // 验证异常被捕获，不会传播
        assertDoesNotThrow(() -> {
            faultyMetricsListener.snapshotState(snapshotContext);
        });
    }

    @Test
    @DisplayName("测试notifyCheckpointComplete异常处理")
    void testNotifyCheckpointCompleteExceptionHandling() throws Exception {
        // 使用一个会抛出异常的CheckpointListener
        CheckpointListener faultyListener = new CheckpointListener() {
            @Override
            public void notifyCheckpointComplete(long checkpointId) {
                throw new RuntimeException("Test exception");
            }
        };
        
        MetricsCheckpointListener<String> faultyMetricsListener = 
            new MetricsCheckpointListener<>(faultyListener);
        
        // 验证异常被捕获，不会传播
        assertDoesNotThrow(() -> {
            faultyMetricsListener.notifyCheckpointComplete(1L);
        });
    }

    @Test
    @DisplayName("测试notifyCheckpointAborted异常处理")
    void testNotifyCheckpointAbortedExceptionHandling() throws Exception {
        // 使用一个会抛出异常的CheckpointListener
        CheckpointListener faultyListener = new CheckpointListener() {
            @Override
            public void notifyCheckpointAborted(long checkpointId, String reason) {
                throw new RuntimeException("Test exception");
            }
        };
        
        MetricsCheckpointListener<String> faultyMetricsListener = 
            new MetricsCheckpointListener<>(faultyListener);
        
        // 验证异常被捕获，不会传播
        assertDoesNotThrow(() -> {
            faultyMetricsListener.notifyCheckpointAborted(1L);
        });
    }

    @Test
    @DisplayName("测试getCheckpointListener")
    void testGetCheckpointListener() {
        CheckpointListener retrieved = listener.getCheckpointListener();
        assertSame(checkpointListener, retrieved);
    }

    @Test
    @DisplayName("测试完整的Checkpoint生命周期")
    void testCompleteCheckpointLifecycle() throws Exception {
        // 1. 初始化状态
        when(initContext.isRestored()).thenReturn(false);
        listener.initializeState(initContext);
        
        // 2. 开始Checkpoint
        when(snapshotContext.getCheckpointId()).thenReturn(1L);
        listener.snapshotState(snapshotContext);
        
        // 3. 等待一段时间
        Thread.sleep(100);
        
        // 4. 完成Checkpoint
        listener.notifyCheckpointComplete(1L);
        
        // 验证整个生命周期
        assertEquals(1, checkpointListener.getTotalCheckpoints());
        assertEquals(1, checkpointListener.getSuccessfulCheckpoints());
        assertEquals(0, checkpointListener.getFailedCheckpoints());
        assertTrue(checkpointListener.getLastCheckpointDuration() >= 100);
    }

    @Test
    @DisplayName("测试Checkpoint失败后继续处理")
    void testContinueAfterCheckpointFailure() throws Exception {
        // 第一个Checkpoint失败
        when(snapshotContext.getCheckpointId()).thenReturn(1L);
        listener.snapshotState(snapshotContext);
        listener.notifyCheckpointAborted(1L);
        
        // 第二个Checkpoint成功
        when(snapshotContext.getCheckpointId()).thenReturn(2L);
        listener.snapshotState(snapshotContext);
        Thread.sleep(50);
        listener.notifyCheckpointComplete(2L);
        
        // 验证系统继续运行
        assertEquals(2, checkpointListener.getTotalCheckpoints());
        assertEquals(1, checkpointListener.getSuccessfulCheckpoints());
        assertEquals(1, checkpointListener.getFailedCheckpoints());
    }
}
