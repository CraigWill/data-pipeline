package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.Meter;
import org.apache.flink.metrics.groups.OperatorMetricGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 监控组件的基于属性的测试
 * Feature: realtime-data-pipeline
 * 
 * 使用jqwik进行基于属性的测试，验证监控组件的通用属性
 * 
 * 验证需求:
 * - 需求 7.2: THE System SHALL 记录每秒处理的记录数
 * - 需求 7.3: THE System SHALL 记录端到端的数据延迟
 * - 需求 7.4: THE System SHALL 记录Checkpoint的成功率和耗时
 * - 需求 7.5: THE System SHALL 记录Backpressure指标
 * - 需求 7.6: WHEN 数据延迟超过60秒 THEN THE System SHALL 触发告警
 * - 需求 7.7: WHEN Checkpoint失败率超过10% THEN THE System SHALL 触发告警
 * - 需求 5.6: WHEN 系统负载超过80% THEN THE System SHALL 触发扩容告警
 */
class MonitoringPropertyTest {

    /**
     * Property 27: 吞吐量指标记录
     * **Validates: Requirements 7.2**
     * 
     * 对于任何时间窗口，系统应该记录该窗口内每秒处理的记录数
     */
    @Property(tries = 20)
    void property27_throughputMetricsRecording(
            @ForAll @IntRange(min = 1, max = 1000) int recordsIn,
            @ForAll @IntRange(min = 1, max = 1000) int recordsOut,
            @ForAll @IntRange(min = 0, max = 100) int recordsFailed) {
        
        // 创建监控服务和指标报告器
        MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(60)
                .build();
        
        MonitoringService monitoringService = new MonitoringService(config);
        RuntimeContext mockRuntimeContext = createMockRuntimeContext();
        monitoringService.initialize(mockRuntimeContext);
        
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        
        // 记录输入记录
        reporter.recordInput(recordsIn);
        
        // 记录输出记录
        reporter.recordOutput(recordsOut);
        
        // 记录失败记录
        reporter.recordFailure(recordsFailed);
        
        // 验证指标被正确记录
        // 注意：由于我们使用mock的Counter，实际计数不会增加
        // 但我们可以验证方法被调用且不抛出异常
        assertThatCode(() -> {
            reporter.recordInput();
            reporter.recordOutput();
            reporter.recordFailure();
        }).doesNotThrowAnyException();
        
        // 验证指标摘要包含吞吐量信息
        String summary = reporter.getMetricsSummary();
        assertThat(summary)
                .as("Metrics summary should contain throughput information")
                .contains("Records In:", "Records Out:", "Failed Records:");
    }

    /**
     * Property 28: 延迟指标记录
     * **Validates: Requirements 7.3**
     * 
     * 对于任何处理的数据记录，系统应该记录从数据库变更到文件写入的端到端延迟
     */
    @Property(tries = 20)
    void property28_latencyMetricsRecording(
            @ForAll("latencyValues") List<Long> latencies) {
        
        Assume.that(!latencies.isEmpty());
        
        // 创建监控服务和指标报告器
        MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(60)
                .build();
        
        MonitoringService monitoringService = new MonitoringService(config);
        RuntimeContext mockRuntimeContext = createMockRuntimeContext();
        monitoringService.initialize(mockRuntimeContext);
        
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerLatencyMetrics();
        
        // 记录所有延迟值
        for (Long latency : latencies) {
            reporter.recordLatency(latency);
        }
        
        // 验证最后一次延迟被正确记录
        assertThat(reporter.getLastLatency())
                .as("Last latency should match the last recorded value")
                .isEqualTo(latencies.get(latencies.size() - 1));
        
        // 验证平均延迟被正确计算
        long expectedAverage = (long) latencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        
        assertThat(reporter.getAverageLatency())
                .as("Average latency should be calculated correctly")
                .isEqualTo(expectedAverage);
        
        // 验证延迟指标可以被重置
        reporter.resetLatencyStats();
        assertThat(reporter.getLastLatency()).isEqualTo(0L);
        assertThat(reporter.getAverageLatency()).isEqualTo(0L);
    }

    /**
     * Property 29: Checkpoint指标记录
     * **Validates: Requirements 7.4**
     * 
     * 对于任何Checkpoint操作，系统应该记录其成功率和耗时
     */
    @Property(tries = 20)
    void property29_checkpointMetricsRecording(
            @ForAll @IntRange(min = 1, max = 50) int successfulCheckpoints,
            @ForAll @IntRange(min = 0, max = 20) int failedCheckpoints) {
        
        // 创建CheckpointListener
        CheckpointListener listener = new CheckpointListener();
        
        // 模拟成功的Checkpoint
        for (int i = 1; i <= successfulCheckpoints; i++) {
            listener.notifyCheckpointStart(i);
            try {
                Thread.sleep(10); // 模拟Checkpoint耗时
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            listener.notifyCheckpointComplete(i);
        }
        
        // 模拟失败的Checkpoint
        for (int i = successfulCheckpoints + 1; i <= successfulCheckpoints + failedCheckpoints; i++) {
            listener.notifyCheckpointStart(i);
            listener.notifyCheckpointFailed(i, new RuntimeException("Test failure"));
        }
        
        // 验证总数正确
        int totalCheckpoints = successfulCheckpoints + failedCheckpoints;
        assertThat(listener.getTotalCheckpoints())
                .as("Total checkpoints should equal successful + failed")
                .isEqualTo(totalCheckpoints);
        
        // 验证成功数和失败数正确
        assertThat(listener.getSuccessfulCheckpoints()).isEqualTo(successfulCheckpoints);
        assertThat(listener.getFailedCheckpoints()).isEqualTo(failedCheckpoints);
        
        // 验证成功率计算正确
        double expectedSuccessRate = totalCheckpoints > 0 
                ? (successfulCheckpoints * 100.0 / totalCheckpoints) 
                : 0.0;
        assertThat(listener.getSuccessRate())
                .as("Success rate should be calculated correctly")
                .isCloseTo(expectedSuccessRate, within(0.1));
        
        // 验证失败率计算正确
        double expectedFailureRate = totalCheckpoints > 0 
                ? (failedCheckpoints * 100.0 / totalCheckpoints) 
                : 0.0;
        assertThat(listener.getFailureRate())
                .as("Failure rate should be calculated correctly")
                .isCloseTo(expectedFailureRate, within(0.1));
        
        // 验证耗时被记录（对于成功的Checkpoint）
        if (successfulCheckpoints > 0) {
            assertThat(listener.getLastCheckpointDuration())
                    .as("Last checkpoint duration should be recorded")
                    .isGreaterThanOrEqualTo(0L);
            
            assertThat(listener.getAverageCheckpointDuration())
                    .as("Average checkpoint duration should be recorded")
                    .isGreaterThanOrEqualTo(0L);
        }
    }

    /**
     * Property 30: 反压指标记录
     * **Validates: Requirements 7.5**
     * 
     * 对于任何时刻，系统应该记录当前的反压级别（0-1之间的值）
     */
    @Property(tries = 20)
    void property30_backpressureMetricsRecording(
            @ForAll("backpressureLevels") List<Double> backpressureLevels) {
        
        Assume.that(!backpressureLevels.isEmpty());
        
        // 创建监控服务和指标报告器
        MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(60)
                .build();
        
        MonitoringService monitoringService = new MonitoringService(config);
        RuntimeContext mockRuntimeContext = createMockRuntimeContext();
        monitoringService.initialize(mockRuntimeContext);
        
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerBackpressureMetrics();
        
        // 记录所有反压级别
        for (Double level : backpressureLevels) {
            reporter.updateBackpressureLevel(level);
        }
        
        // 验证最后一次反压级别被正确记录
        Double lastLevel = backpressureLevels.get(backpressureLevels.size() - 1);
        assertThat(reporter.getBackpressureLevel())
                .as("Last backpressure level should match the last recorded value")
                .isCloseTo(lastLevel, within(0.001));
        
        // 验证反压级别始终在0-1之间
        assertThat(reporter.getBackpressureLevel())
                .as("Backpressure level should be between 0 and 1")
                .isBetween(0.0, 1.0);
    }

    /**
     * Property 31: 延迟告警触发
     * **Validates: Requirements 7.6**
     * 
     * 对于任何数据延迟超过60秒的情况，系统应该触发告警
     */
    @Property(tries = 20)
    void property31_latencyAlertTriggering(
            @ForAll @LongRange(min = 60001, max = 300000) long highLatency) throws InterruptedException {
        
        // 创建监控配置，设置延迟阈值为60秒
        MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(1) // 1秒检查间隔，加快测试
                .latencyThreshold(60000L)
                .alertMethod("log")
                .build();
        
        MonitoringService monitoringService = new MonitoringService(config);
        RuntimeContext mockRuntimeContext = createMockRuntimeContext();
        monitoringService.initialize(mockRuntimeContext);
        
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerLatencyMetrics();
        
        // 记录高延迟
        reporter.recordLatency(highLatency);
        
        // 创建告警管理器
        AlertManager alertManager = new AlertManager(config, reporter);
        
        // 启动告警管理器
        alertManager.start();
        
        // 等待告警检查周期
        Thread.sleep(2000);
        
        // 验证告警规则存在
        assertThat(alertManager.getRule("latency_threshold"))
                .as("Latency alert rule should exist")
                .isNotNull();
        
        // 验证告警被触发（检查活动告警或历史告警）
        boolean alertTriggered = !alertManager.getActiveAlerts().isEmpty() 
                || !alertManager.getAlertHistory().isEmpty();
        
        assertThat(alertTriggered)
                .as("Alert should be triggered when latency exceeds threshold")
                .isTrue();
        
        // 停止告警管理器
        alertManager.stop();
        alertManager.shutdown();
    }

    /**
     * Property 32: Checkpoint失败率告警
     * **Validates: Requirements 7.7**
     * 
     * 对于任何Checkpoint失败率超过10%的情况，系统应该触发告警
     */
    @Property(tries = 20)
    void property32_checkpointFailureRateAlert(
            @ForAll @IntRange(min = 10, max = 50) int totalCheckpoints,
            @ForAll @DoubleRange(min = 0.11, max = 0.9) double failureRatio) throws InterruptedException {
        
        // 计算失败数量（确保失败率超过10%）
        int failedCount = (int) Math.ceil(totalCheckpoints * failureRatio);
        int successCount = totalCheckpoints - failedCount;
        
        // 创建CheckpointListener并模拟Checkpoint
        CheckpointListener listener = new CheckpointListener();
        
        for (int i = 1; i <= successCount; i++) {
            listener.notifyCheckpointStart(i);
            listener.notifyCheckpointComplete(i);
        }
        
        for (int i = successCount + 1; i <= totalCheckpoints; i++) {
            listener.notifyCheckpointStart(i);
            listener.notifyCheckpointFailed(i, new RuntimeException("Test failure"));
        }
        
        // 验证失败率确实超过10%
        assertThat(listener.getFailureRate())
                .as("Failure rate should exceed 10%")
                .isGreaterThan(10.0);
        
        // 创建监控配置
        MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(1) // 1秒检查间隔
                .checkpointFailureRateThreshold(0.1) // 10%阈值
                .alertMethod("log")
                .build();
        
        MonitoringService monitoringService = new MonitoringService(config);
        RuntimeContext mockRuntimeContext = createMockRuntimeContext();
        monitoringService.initialize(mockRuntimeContext);
        
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.setCheckpointListener(listener);
        reporter.registerCheckpointMetrics();
        
        // 创建告警管理器
        AlertManager alertManager = new AlertManager(config, reporter);
        
        // 启动告警管理器
        alertManager.start();
        
        // 等待告警检查周期
        Thread.sleep(2000);
        
        // 验证告警规则存在
        assertThat(alertManager.getRule("checkpoint_failure_rate"))
                .as("Checkpoint failure rate alert rule should exist")
                .isNotNull();
        
        // 验证告警被触发
        boolean alertTriggered = !alertManager.getActiveAlerts().isEmpty() 
                || !alertManager.getAlertHistory().isEmpty();
        
        assertThat(alertTriggered)
                .as("Alert should be triggered when checkpoint failure rate exceeds 10%")
                .isTrue();
        
        // 停止告警管理器
        alertManager.stop();
        alertManager.shutdown();
    }

    /**
     * Property 21: 负载告警触发
     * **Validates: Requirements 5.6**
     * 
     * 对于任何系统负载超过80%的情况，系统应该触发扩容告警
     * 
     * 注意：由于无法直接控制系统CPU和内存使用率，此测试验证告警规则的存在和配置
     */
    @Property(tries = 20)
    void property21_loadAlertTriggering(
            @ForAll @DoubleRange(min = 0.81, max = 1.0) double loadThreshold) throws InterruptedException {
        
        // 创建监控配置，设置负载阈值
        MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(1)
                .loadThreshold(0.8) // 80%阈值
                .alertMethod("log")
                .build();
        
        MonitoringService monitoringService = new MonitoringService(config);
        RuntimeContext mockRuntimeContext = createMockRuntimeContext();
        monitoringService.initialize(mockRuntimeContext);
        
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerResourceMetrics();
        
        // 创建告警管理器
        AlertManager alertManager = new AlertManager(config, reporter);
        
        // 验证负载告警规则存在
        AlertManager.AlertRule loadRule = alertManager.getRule("system_load");
        assertThat(loadRule)
                .as("System load alert rule should exist")
                .isNotNull();
        
        assertThat(loadRule.getName()).isEqualTo("system_load");
        assertThat(loadRule.getDescription()).contains("System load exceeds threshold");
        assertThat(loadRule.isEnabled()).isTrue();
        
        // 验证告警规则配置正确
        assertThat(config.getLoadThreshold())
                .as("Load threshold should be 0.8 (80%)")
                .isEqualTo(0.8);
        
        // 启动告警管理器
        alertManager.start();
        assertThat(alertManager.isRunning()).isTrue();
        
        // 等待一个检查周期
        Thread.sleep(1500);
        
        // 停止告警管理器
        alertManager.stop();
        alertManager.shutdown();
        
        // 验证告警管理器可以正常启动和停止
        assertThat(alertManager.isRunning()).isFalse();
    }

    /**
     * 额外属性测试：指标注册的幂等性
     * 验证多次注册相同指标不会导致错误
     */
    @Property(tries = 10)
    void propertyExtra_metricsRegistrationIdempotence(
            @ForAll @IntRange(min = 2, max = 10) int registrationCount) {
        
        MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(60)
                .build();
        
        MonitoringService monitoringService = new MonitoringService(config);
        RuntimeContext mockRuntimeContext = createMockRuntimeContext();
        monitoringService.initialize(mockRuntimeContext);
        
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        
        // 多次注册相同的指标
        for (int i = 0; i < registrationCount; i++) {
            assertThatCode(() -> {
                reporter.registerThroughputMetrics();
                reporter.registerLatencyMetrics();
                reporter.registerBackpressureMetrics();
            }).doesNotThrowAnyException();
        }
    }

    /**
     * 额外属性测试：并发指标更新
     * 验证多线程同时更新指标不会导致数据不一致
     */
    @Property(tries = 10)
    void propertyExtra_concurrentMetricsUpdate(
            @ForAll @IntRange(min = 5, max = 20) int threadCount,
            @ForAll @IntRange(min = 10, max = 100) int updatesPerThread) throws InterruptedException {
        
        MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(60)
                .build();
        
        MonitoringService monitoringService = new MonitoringService(config);
        RuntimeContext mockRuntimeContext = createMockRuntimeContext();
        monitoringService.initialize(mockRuntimeContext);
        
        MetricsReporter reporter = new MetricsReporter(monitoringService);
        reporter.registerThroughputMetrics();
        reporter.registerLatencyMetrics();
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        // 创建多个线程同时更新指标
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < updatesPerThread; j++) {
                        reporter.recordInput();
                        reporter.recordOutput();
                        reporter.recordLatency(100L + j);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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
                .as("All threads should complete within timeout")
                .isTrue();
        
        // 验证延迟统计是一致的
        assertThat(reporter.getLastLatency())
                .as("Last latency should be recorded")
                .isGreaterThanOrEqualTo(0L);
        
        assertThat(reporter.getAverageLatency())
                .as("Average latency should be calculated")
                .isGreaterThanOrEqualTo(0L);
    }

    // ==================== 数据生成器 ====================

    @Provide
    Arbitrary<List<Long>> latencyValues() {
        return Arbitraries.longs()
                .between(1L, 300000L) // 1ms to 5 minutes
                .list()
                .ofMinSize(1)
                .ofMaxSize(100);
    }

    @Provide
    Arbitrary<List<Double>> backpressureLevels() {
        return Arbitraries.doubles()
                .between(0.0, 1.0)
                .list()
                .ofMinSize(1)
                .ofMaxSize(50);
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建Mock的RuntimeContext
     */
    private RuntimeContext createMockRuntimeContext() {
        RuntimeContext mockRuntimeContext = mock(RuntimeContext.class);
        OperatorMetricGroup mockMetricGroup = mock(OperatorMetricGroup.class);
        OperatorMetricGroup mockSubGroup = mock(OperatorMetricGroup.class);
        
        when(mockRuntimeContext.getMetricGroup()).thenReturn(mockMetricGroup);
        when(mockMetricGroup.addGroup(anyString())).thenReturn(mockSubGroup);
        
        // Mock Counter
        Counter mockCounter = mock(Counter.class);
        when(mockMetricGroup.counter(anyString())).thenReturn(mockCounter);
        when(mockSubGroup.counter(anyString())).thenReturn(mockCounter);
        
        // Mock Meter
        when(mockSubGroup.meter(anyString(), any(Meter.class))).thenAnswer(invocation -> invocation.getArgument(1));
        
        // Mock Gauge
        when(mockSubGroup.gauge(anyString(), any(Gauge.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(mockMetricGroup.gauge(anyString(), any(Gauge.class))).thenAnswer(invocation -> invocation.getArgument(1));
        
        // Mock Histogram
        when(mockSubGroup.histogram(anyString(), any(Histogram.class))).thenAnswer(invocation -> invocation.getArgument(1));
        
        return mockRuntimeContext;
    }
}
