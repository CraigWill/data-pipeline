package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 指标收集示例
 * 演示如何在Flink作业中集成和使用MetricsReporter收集关键指标
 * 
 * 本示例展示:
 * 1. 吞吐量指标收集（每秒记录数）
 * 2. 延迟指标收集（端到端延迟P50/P99）
 * 3. Checkpoint指标收集（成功率、耗时）
 * 4. 反压指标收集
 * 5. 资源使用指标收集（CPU、内存）
 * 
 * 验证需求:
 * - 需求 7.2: THE System SHALL 记录每秒处理的记录数
 * - 需求 7.3: THE System SHALL 记录端到端的数据延迟
 * - 需求 7.4: THE System SHALL 记录Checkpoint的成功率和耗时
 * - 需求 7.5: THE System SHALL 记录Backpressure指标
 */
public class MetricsCollectionExample extends RichMapFunction<ChangeEvent, ProcessedEvent> {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectionExample.class);

    private transient MonitoringService monitoringService;
    private transient MetricsReporter metricsReporter;
    private transient CheckpointListener checkpointListener;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        // 1. 创建监控配置
        MonitoringConfig config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(60)
            .latencyThreshold(60000L)
            .checkpointFailureRateThreshold(0.1)
            .build();
        
        // 2. 初始化监控服务
        monitoringService = new MonitoringService(config);
        monitoringService.initialize(getRuntimeContext());
        
        // 3. 创建指标报告器
        metricsReporter = new MetricsReporter(monitoringService);
        
        // 4. 创建Checkpoint监听器
        checkpointListener = new CheckpointListener();
        metricsReporter.setCheckpointListener(checkpointListener);
        
        // 5. 注册所有指标
        metricsReporter.registerAllMetrics();
        
        logger.info("Metrics collection initialized successfully");
        logger.info("Registered metrics: {}", monitoringService.getMetricCount());
    }

    @Override
    public ProcessedEvent map(ChangeEvent event) throws Exception {
        long startTime = System.currentTimeMillis();
        
        try {
            // 记录输入
            metricsReporter.recordInput();
            
            // 处理事件
            ProcessedEvent processed = processEvent(event);
            
            // 计算端到端延迟
            long latency = System.currentTimeMillis() - event.getTimestamp();
            metricsReporter.recordLatency(latency);
            
            // 记录输出
            metricsReporter.recordOutput();
            
            // 模拟反压检测（实际应该从Flink获取）
            updateBackpressure();
            
            // 定期输出指标摘要
            if (processed.getEventId().hashCode() % 1000 == 0) {
                logger.info("Metrics Summary:\n{}", metricsReporter.getMetricsSummary());
            }
            
            return processed;
            
        } catch (Exception e) {
            // 记录失败
            metricsReporter.recordFailure();
            logger.error("Failed to process event: {}", event.getEventId(), e);
            throw e;
        }
    }

    /**
     * 处理事件
     */
    private ProcessedEvent processEvent(ChangeEvent event) {
        return ProcessedEvent.builder()
            .eventType(event.getEventType())
            .database(event.getDatabase())
            .table(event.getTable())
            .timestamp(event.getTimestamp())
            .processTime(System.currentTimeMillis())
            .data(event.getAfter() != null ? event.getAfter() : event.getBefore())
            .eventId(event.getEventId())
            .partition("default")
            .build();
    }

    /**
     * 更新反压指标
     * 实际实现中应该从Flink的BackpressureMonitor获取真实的反压数据
     */
    private void updateBackpressure() {
        // 这里使用简化的模拟逻辑
        // 实际应该通过Flink的MetricGroup获取反压信息
        double simulatedBackpressure = Math.random() * 0.5; // 模拟0-0.5的反压
        metricsReporter.updateBackpressureLevel(simulatedBackpressure);
    }

    @Override
    public void close() throws Exception {
        // 输出最终的指标摘要
        if (metricsReporter != null) {
            logger.info("Final Metrics Summary:\n{}", metricsReporter.getMetricsSummary());
        }
        
        if (monitoringService != null) {
            logger.info("Monitoring Service Summary:\n{}", monitoringService.getMetricsSummary());
        }
        
        super.close();
    }

    /**
     * 使用示例
     */
    public static void main(String[] args) {
        logger.info("=== Metrics Collection Example ===");
        logger.info("");
        logger.info("This example demonstrates how to collect key metrics in a Flink job:");
        logger.info("");
        logger.info("1. Throughput Metrics:");
        logger.info("   - records.in: Number of input records");
        logger.info("   - records.out: Number of output records");
        logger.info("   - records.failed: Number of failed records");
        logger.info("   - records.in.rate: Input rate (records/second)");
        logger.info("   - records.out.rate: Output rate (records/second)");
        logger.info("");
        logger.info("2. Latency Metrics:");
        logger.info("   - latency.average: Average end-to-end latency");
        logger.info("   - latency.last: Last recorded latency");
        logger.info("   - latency.p50: P50 (median) latency");
        logger.info("   - latency.p99: P99 latency");
        logger.info("   - latency.histogram: Latency distribution");
        logger.info("");
        logger.info("3. Checkpoint Metrics:");
        logger.info("   - checkpoint.success.rate: Checkpoint success rate (%)");
        logger.info("   - checkpoint.failure.rate: Checkpoint failure rate (%)");
        logger.info("   - checkpoint.duration.average: Average checkpoint duration");
        logger.info("   - checkpoint.duration.last: Last checkpoint duration");
        logger.info("   - checkpoint.total.count: Total checkpoint count");
        logger.info("   - checkpoint.success.count: Successful checkpoint count");
        logger.info("   - checkpoint.failure.count: Failed checkpoint count");
        logger.info("");
        logger.info("4. Backpressure Metrics:");
        logger.info("   - backpressure.level: Backpressure level (0-1)");
        logger.info("   - backpressure.status: Backpressure status (OK/LOW/MEDIUM/HIGH)");
        logger.info("");
        logger.info("5. Resource Usage Metrics:");
        logger.info("   - resource.cpu.usage: CPU usage (0-1)");
        logger.info("   - resource.memory.usage: Memory usage (0-1)");
        logger.info("   - resource.heap.used: Heap memory used (bytes)");
        logger.info("   - resource.heap.total: Total heap memory (bytes)");
        logger.info("   - resource.heap.max: Maximum heap memory (bytes)");
        logger.info("");
        logger.info("Usage in Flink Job:");
        logger.info("  DataStream<ChangeEvent> source = ...;");
        logger.info("  DataStream<ProcessedEvent> processed = source");
        logger.info("      .map(new MetricsCollectionExample())");
        logger.info("      .name(\"Event Processor with Metrics\");");
        logger.info("");
        logger.info("All metrics are automatically exposed through Flink's Metrics system");
        logger.info("and can be accessed via JMX, REST API, or custom reporters.");
    }
}
