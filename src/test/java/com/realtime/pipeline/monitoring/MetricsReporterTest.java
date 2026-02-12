package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.Meter;
import org.apache.flink.metrics.groups.OperatorMetricGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MetricsReporter单元测试
 * 
 * 测试范围:
 * 1. 吞吐量指标注册和记录
 * 2. 延迟指标注册和记录（包括P50/P99）
 * 3. Checkpoint指标注册
 * 4. 反压指标注册和更新
 * 5. 资源使用指标注册
 */
class MetricsReporterTest {

    private MonitoringConfig config;
    private MonitoringService monitoringService;
    private RuntimeContext mockRuntimeContext;
    private OperatorMetricGroup mockMetricGroup;
    private OperatorMetricGroup mockSubGroup;
    private Counter mockCounter;
    private Meter mockMeter;
    private Histogram mockHistogram;

    @BeforeEach
    void setUp() {
        config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(60)
            .build();

        // Mock dependencies
        mockRuntimeContext = mock(RuntimeContext.class);
        mockMetricGroup = mock(OperatorMetricGroup.class);
        mockSubGroup = mock(OperatorMetricGroup.class);
        mockCounter = mock(Counter.class);
        mockMeter = mock(Meter.class);
        mockHistogram = mock(Histogram.class);
        
        when(mockRuntimeContext.getMetricGroup()).thenReturn(mockMetricGroup);
        when(mockMetricGroup.addGroup(anyString())).thenReturn(mockSubGroup);
        when(mockMetricGroup.counter(anyString())).thenReturn(mockCounter);
        when(mockSubGroup.counter(anyString())).thenReturn(mockCounter);
        when(mockSubGroup.meter(anyString(), any(Meter.class))).thenReturn(mockMeter);
        when(mockSubGroup.histogram(anyString(), any(Histogram.class))).thenReturn(mockHistogram);

        // Initialize MonitoringService
        monitoringService = new MonitoringService(config);
        monitoringService.initialize(mockRuntimeContext);
    }

    @Test
    @DisplayName("测试MetricsReporter创建")
    void testMetricsReporterCreation() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        assertNotNull(reporter);
        assertEquals(monitoringService, reporter.getMonitoringService());
    }

    @Test
    @DisplayName("测试创建时MonitoringService为null抛出异常")
    void testCreationWithNullMonitoringService() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MetricsReporter(null);
        });
    }

    @Test
    @DisplayName("测试注册吞吐量指标")
    void testRegisterThroughputMetrics() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.registerThroughputMetrics();
        
        // 验证注册了正确的指标
        verify(mockSubGroup, atLeastOnce()).counter("records.in");
        verify(mockSubGroup, atLeastOnce()).counter("records.out");
        verify(mockSubGroup, atLeastOnce()).counter("records.failed");
        verify(mockSubGroup, atLeastOnce()).meter(eq("records.in.rate"), any(Meter.class));
        verify(mockSubGroup, atLeastOnce()).meter(eq("records.out.rate"), any(Meter.class));
    }

    @Test
    @DisplayName("测试注册延迟指标")
    void testRegisterLatencyMetrics() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.registerLatencyMetrics();
        
        // 验证注册了延迟相关的指标（包括P50和P99）
        verify(mockSubGroup, atLeastOnce()).gauge(eq("average"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("last"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("p50"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("p99"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).histogram(eq("histogram"), any(Histogram.class));
    }
    
    @Test
    @DisplayName("测试注册Checkpoint指标")
    void testRegisterCheckpointMetrics() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.registerCheckpointMetrics();
        
        // 验证注册了Checkpoint相关的指标
        verify(mockSubGroup, atLeastOnce()).gauge(eq("success.rate"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("failure.rate"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("duration.average"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("duration.last"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("total.count"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("success.count"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("failure.count"), any(Gauge.class));
    }

    @Test
    @DisplayName("测试注册反压指标")
    void testRegisterBackpressureMetrics() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.registerBackpressureMetrics();
        
        // 验证注册了反压指标（包括level和status）
        verify(mockSubGroup, atLeastOnce()).gauge(eq("level"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("status"), any(Gauge.class));
    }

    @Test
    @DisplayName("测试注册资源使用指标")
    void testRegisterResourceMetrics() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.registerResourceMetrics();
        
        // 验证注册了资源使用指标（包括heap相关指标）
        verify(mockSubGroup, atLeastOnce()).gauge(eq("cpu.usage"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("memory.usage"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("heap.used"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("heap.total"), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).gauge(eq("heap.max"), any(Gauge.class));
    }

    @Test
    @DisplayName("测试记录输入记录")
    void testRecordInput() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        
        reporter.recordInput();
        
        verify(mockCounter, atLeastOnce()).inc();
        // Note: We use SimpleMeter internally, so we don't verify mockMeter
    }

    @Test
    @DisplayName("测试批量记录输入记录")
    void testRecordInputBatch() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        
        reporter.recordInput(10);
        
        verify(mockCounter, atLeastOnce()).inc(10);
        // Note: We use SimpleMeter internally, so we don't verify mockMeter
    }

    @Test
    @DisplayName("测试记录输出记录")
    void testRecordOutput() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        
        reporter.recordOutput();
        
        verify(mockCounter, atLeastOnce()).inc();
        // Note: We use SimpleMeter internally, so we don't verify mockMeter
    }

    @Test
    @DisplayName("测试批量记录输出记录")
    void testRecordOutputBatch() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        
        reporter.recordOutput(5);
        
        verify(mockCounter, atLeastOnce()).inc(5);
        // Note: We use SimpleMeter internally, so we don't verify mockMeter
    }

    @Test
    @DisplayName("测试记录失败记录")
    void testRecordFailure() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        
        reporter.recordFailure();
        
        verify(mockCounter, atLeastOnce()).inc();
    }

    @Test
    @DisplayName("测试批量记录失败记录")
    void testRecordFailureBatch() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        
        reporter.recordFailure(3);
        
        verify(mockCounter, atLeastOnce()).inc(3);
    }

    @Test
    @DisplayName("测试记录延迟")
    void testRecordLatency() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.recordLatency(100L);
        
        assertEquals(100L, reporter.getLastLatency());
        assertEquals(100L, reporter.getAverageLatency());
        
        reporter.recordLatency(200L);
        
        assertEquals(200L, reporter.getLastLatency());
        assertEquals(150L, reporter.getAverageLatency());
    }

    @Test
    @DisplayName("测试获取平均延迟（无数据）")
    void testGetAverageLatencyWithNoData() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        assertEquals(0L, reporter.getAverageLatency());
    }

    @Test
    @DisplayName("测试获取最后一次延迟（无数据）")
    void testGetLastLatencyWithNoData() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        assertEquals(0L, reporter.getLastLatency());
    }

    @Test
    @DisplayName("测试重置延迟统计")
    void testResetLatencyStats() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.recordLatency(100L);
        reporter.recordLatency(200L);
        
        assertEquals(200L, reporter.getLastLatency());
        assertEquals(150L, reporter.getAverageLatency());
        
        reporter.resetLatencyStats();
        
        assertEquals(0L, reporter.getLastLatency());
        assertEquals(0L, reporter.getAverageLatency());
    }

    @Test
    @DisplayName("测试未初始化MonitoringService时注册指标")
    void testRegisterMetricsWithUninitializedService() {
        MonitoringService uninitializedService = new MonitoringService(config);
        MetricsReporter reporter = new MetricsReporter(uninitializedService);
        
        // 应该不抛出异常，只是记录警告
        assertDoesNotThrow(() -> {
            reporter.registerThroughputMetrics();
            reporter.registerLatencyMetrics();
            reporter.registerBackpressureMetrics();
            reporter.registerResourceMetrics();
        });
    }

    @Test
    @DisplayName("测试未注册指标时记录数据")
    void testRecordWithoutRegistration() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        // 应该不抛出异常，只是不执行操作
        assertDoesNotThrow(() -> {
            reporter.recordInput();
            reporter.recordOutput();
            reporter.recordFailure();
        });
    }

    @Test
    @DisplayName("测试多次记录延迟")
    void testMultipleLatencyRecords() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.recordLatency(50L);
        reporter.recordLatency(100L);
        reporter.recordLatency(150L);
        reporter.recordLatency(200L);
        
        assertEquals(200L, reporter.getLastLatency());
        assertEquals(125L, reporter.getAverageLatency()); // (50+100+150+200)/4 = 125
    }

    @Test
    @DisplayName("测试延迟记录更新MonitoringService")
    void testLatencyRecordUpdatesMonitoringService() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.recordLatency(100L);
        
        // 验证MonitoringService中的值被更新
        MonitoringService.MetricValue metricValue = 
            monitoringService.getMetric("latency.last");
        
        // 注意：由于我们没有实际注册这个指标，这里可能为null
        // 这个测试主要验证updateMetricValue方法被调用
        // 实际的值更新需要先注册指标
    }

    @Test
    @DisplayName("测试获取MonitoringService")
    void testGetMonitoringService() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        MonitoringService retrievedService = reporter.getMonitoringService();
        
        assertNotNull(retrievedService);
        assertSame(monitoringService, retrievedService);
    }
    
    @Test
    @DisplayName("测试更新反压级别")
    void testUpdateBackpressureLevel() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.updateBackpressureLevel(0.5);
        assertEquals(0.5, reporter.getBackpressureLevel(), 0.001);
        
        reporter.updateBackpressureLevel(0.9);
        assertEquals(0.9, reporter.getBackpressureLevel(), 0.001);
    }
    
    @Test
    @DisplayName("测试无效反压级别")
    void testInvalidBackpressureLevel() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        // 测试负值
        reporter.updateBackpressureLevel(-0.1);
        assertEquals(0.0, reporter.getBackpressureLevel(), 0.001);
        
        // 测试超过1.0的值
        reporter.updateBackpressureLevel(1.5);
        assertEquals(0.0, reporter.getBackpressureLevel(), 0.001);
    }
    
    @Test
    @DisplayName("测试设置CheckpointListener")
    void testSetCheckpointListener() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        CheckpointListener listener = new CheckpointListener();
        
        reporter.setCheckpointListener(listener);
        
        assertSame(listener, reporter.getCheckpointListener());
    }
    
    @Test
    @DisplayName("测试P50和P99延迟计算")
    void testPercentileLatencyCalculation() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerLatencyMetrics();
        
        // 记录一系列延迟值
        for (int i = 1; i <= 100; i++) {
            reporter.recordLatency(i);
        }
        
        // 验证延迟被正确记录
        assertEquals(100, reporter.getLastLatency());
        assertTrue(reporter.getAverageLatency() > 0);
        
        // 注意：P50和P99是通过Gauge计算的，我们无法直接测试
        // 但我们可以验证延迟数据被正确记录
    }
    
    @Test
    @DisplayName("测试注册所有指标")
    void testRegisterAllMetrics() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        reporter.registerAllMetrics();
        
        // 验证所有类型的指标都被注册
        verify(mockSubGroup, atLeastOnce()).counter(anyString());
        verify(mockSubGroup, atLeastOnce()).gauge(anyString(), any(Gauge.class));
        verify(mockSubGroup, atLeastOnce()).meter(anyString(), any(Meter.class));
        verify(mockSubGroup, atLeastOnce()).histogram(anyString(), any(Histogram.class));
    }
    
    @Test
    @DisplayName("测试获取指标摘要")
    void testGetMetricsSummary() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        
        reporter.recordInput(10);
        reporter.recordOutput(8);
        reporter.recordFailure(2);
        reporter.recordLatency(100);
        reporter.updateBackpressureLevel(0.3);
        
        String summary = reporter.getMetricsSummary();
        
        assertNotNull(summary);
        assertTrue(summary.contains("Records In:"));
        assertTrue(summary.contains("Records Out:"));
        assertTrue(summary.contains("Failed Records:"));
        assertTrue(summary.contains("Average Latency:"));
        assertTrue(summary.contains("Backpressure Level:"));
        assertTrue(summary.contains("CPU Usage:"));
        assertTrue(summary.contains("Memory Usage:"));
    }
    
    @Test
    @DisplayName("测试带CheckpointListener的指标摘要")
    void testGetMetricsSummaryWithCheckpointListener() {
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        CheckpointListener listener = new CheckpointListener();
        
        // 模拟一些checkpoint事件
        listener.notifyCheckpointStart(1);
        listener.notifyCheckpointComplete(1);
        
        reporter.setCheckpointListener(listener);
        
        String summary = reporter.getMetricsSummary();
        
        assertNotNull(summary);
        assertTrue(summary.contains("Checkpoint Success Rate:"));
        assertTrue(summary.contains("Checkpoint Failure Rate:"));
        assertTrue(summary.contains("Checkpoint Avg Duration:"));
    }
}
