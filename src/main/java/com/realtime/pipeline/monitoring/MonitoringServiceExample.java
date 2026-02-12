package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * MonitoringService使用示例
 * 演示如何在Flink作业中集成和使用MonitoringService
 */
public class MonitoringServiceExample {

    /**
     * 示例：在Flink作业中使用MonitoringService
     */
    public static void main(String[] args) throws Exception {
        // 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 创建监控配置
        MonitoringConfig config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(60)
            .latencyThreshold(60000L)
            .checkpointFailureRateThreshold(0.1)
            .build();
        
        // 创建数据流（示例）
        DataStream<ProcessedEvent> stream = env.fromElements(
            ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .build()
        );
        
        // 应用带监控的处理函数
        stream.map(new MonitoredMapFunction(config))
            .name("Monitored Processor");
        
        // 执行作业
        env.execute("Monitoring Service Example");
    }

    /**
     * 带监控的Map函数示例
     */
    public static class MonitoredMapFunction extends RichMapFunction<ProcessedEvent, ProcessedEvent> {
        private static final long serialVersionUID = 1L;
        
        private final MonitoringConfig config;
        private transient MonitoringService monitoringService;
        private transient MetricsReporter metricsReporter;
        private transient Counter processedCounter;
        private transient long startTime;

        public MonitoredMapFunction(MonitoringConfig config) {
            this.config = config;
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            // 初始化监控服务
            monitoringService = new MonitoringService(config);
            monitoringService.initialize(getRuntimeContext());
            
            // 创建Metrics报告器
            metricsReporter = new MetricsReporter(monitoringService);
            
            // 注册各类指标
            metricsReporter.registerThroughputMetrics();
            metricsReporter.registerLatencyMetrics();
            metricsReporter.registerBackpressureMetrics();
            metricsReporter.registerResourceMetrics();
            
            // 注册自定义Counter
            processedCounter = monitoringService.registerCounter("custom", "processed.events");
            
            // 注册自定义Gauge
            startTime = System.currentTimeMillis();
            monitoringService.registerGauge("custom", "uptime.seconds", 
                (Gauge<Long>) () -> (System.currentTimeMillis() - startTime) / 1000);
            
            System.out.println("MonitoringService initialized");
            System.out.println(monitoringService.getMetricsSummary());
        }

        @Override
        public ProcessedEvent map(ProcessedEvent event) throws Exception {
            long eventStartTime = System.currentTimeMillis();
            
            // 记录输入
            metricsReporter.recordInput();
            
            // 处理事件（示例）
            ProcessedEvent processed = ProcessedEvent.builder()
                .eventType(event.getEventType())
                .database(event.getDatabase())
                .table(event.getTable())
                .timestamp(event.getTimestamp())
                .processTime(System.currentTimeMillis())
                .data(event.getData())
                .partition(event.getPartition())
                .eventId(event.getEventId())
                .build();
            
            // 记录输出
            metricsReporter.recordOutput();
            
            // 记录延迟
            long latency = System.currentTimeMillis() - eventStartTime;
            metricsReporter.recordLatency(latency);
            
            // 更新自定义Counter
            processedCounter.inc();
            
            return processed;
        }

        @Override
        public void close() throws Exception {
            super.close();
            
            // 打印最终指标摘要
            if (monitoringService != null) {
                System.out.println("Final Metrics Summary:");
                System.out.println(monitoringService.getMetricsSummary());
                System.out.println("Average Latency: " + metricsReporter.getAverageLatency() + " ms");
            }
        }
    }
}
