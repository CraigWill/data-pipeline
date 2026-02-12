package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.flink.FlinkEnvironmentConfigurator;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 精确一次语义示例
 * 演示如何配置和使用精确一次语义的文件Sink
 * 
 * 实现需求:
 * - 9.2: 支持精确一次（Exactly-once）语义
 * - 9.3: 通过幂等性操作去重
 */
public class ExactlyOnceExample {
    private static final Logger logger = LoggerFactory.getLogger(ExactlyOnceExample.class);

    public static void main(String[] args) throws Exception {
        // 示例1: 使用精确一次语义
        exactlyOnceExample();
        
        // 示例2: 使用至少一次语义
        atLeastOnceExample();
        
        // 示例3: 完整的Flink作业配置
        completeJobExample();
    }

    /**
     * 示例1: 配置精确一次语义的Sink
     */
    public static void exactlyOnceExample() {
        logger.info("=== Exactly-Once Semantics Example ===");
        
        // 创建输出配置
        OutputConfig outputConfig = OutputConfig.builder()
                .path("/data/output/exactly-once")
                .format("json")
                .rollingSizeBytes(1024 * 1024 * 1024L) // 1GB
                .rollingIntervalMs(60 * 60 * 1000L)    // 1小时
                .compression("none")
                .maxRetries(3)
                .retryBackoff(2)
                .build();
        
        // 使用构建器创建精确一次语义的Sink
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
                .forFormat("json", outputConfig)
                .withIdempotence(true)                          // 启用幂等性
                .withStateRetention(24 * 60 * 60 * 1000L)      // 状态保留24小时
                .withDeadLetterQueue("/data/dlq/exactly-once") // 启用死信队列
                .build();
        
        logger.info("Created exactly-once sink with idempotence and DLQ");
        
        // 或者使用快捷方法
        SinkFunction<ProcessedEvent> sink2 = ExactlyOnceSinkBuilder.createExactlyOnceSink(
                "json", outputConfig, "/data/dlq/exactly-once");
        
        logger.info("Created exactly-once sink using shortcut method");
    }

    /**
     * 示例2: 配置至少一次语义的Sink
     */
    public static void atLeastOnceExample() {
        logger.info("=== At-Least-Once Semantics Example ===");
        
        OutputConfig outputConfig = OutputConfig.builder()
                .path("/data/output/at-least-once")
                .format("csv")
                .rollingSizeBytes(1024 * 1024 * 1024L)
                .rollingIntervalMs(60 * 60 * 1000L)
                .compression("gzip")
                .maxRetries(3)
                .retryBackoff(2)
                .build();
        
        // 创建至少一次语义的Sink（不启用幂等性）
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
                .forFormat("csv", outputConfig)
                .withIdempotence(false)                        // 不启用幂等性
                .withDeadLetterQueue("/data/dlq/at-least-once")
                .build();
        
        logger.info("Created at-least-once sink without idempotence");
    }

    /**
     * 示例3: 完整的Flink作业配置
     */
    public static void completeJobExample() throws Exception {
        logger.info("=== Complete Flink Job Example ===");
        
        // 1. 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 2. 配置Flink环境（精确一次语义）
        FlinkConfig flinkConfig = FlinkConfig.builder()
                .parallelism(4)
                .checkpointInterval(300000L)              // 5分钟
                .checkpointTimeout(600000L)               // 10分钟
                .minPauseBetweenCheckpoints(60000L)       // 1分钟
                .maxConcurrentCheckpoints(1)
                .checkpointingMode("exactly-once")        // 精确一次模式
                .retainedCheckpoints(3)
                .stateBackendType("hashmap")
                .checkpointDir("file:///data/checkpoints")
                .build();
        
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);
        
        logger.info("Configured Flink environment with exactly-once checkpointing");
        
        // 3. 创建数据源（示例）
        DataStream<ProcessedEvent> dataStream = env.fromElements(
                createSampleEvent("evt-001", "db1", "table1", 1000L),
                createSampleEvent("evt-002", "db1", "table1", 2000L),
                createSampleEvent("evt-001", "db1", "table1", 1000L), // 重复事件
                createSampleEvent("evt-003", "db1", "table2", 3000L)
        );
        
        // 4. 创建精确一次语义的Sink
        OutputConfig outputConfig = OutputConfig.builder()
                .path("/data/output/complete-example")
                .format("json")
                .rollingSizeBytes(1024 * 1024 * 1024L)
                .rollingIntervalMs(60 * 60 * 1000L)
                .compression("none")
                .maxRetries(3)
                .retryBackoff(2)
                .build();
        
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder.createExactlyOnceSink(
                "json", outputConfig, "/data/dlq/complete-example");
        
        // 5. 添加Sink到数据流
        dataStream.addSink(sink).name("Exactly-Once File Sink");
        
        logger.info("Added exactly-once sink to data stream");
        
        // 6. 执行作业
        // env.execute("Exactly-Once Semantics Example");
        logger.info("Job configured (not executed in example)");
    }

    /**
     * 创建示例事件
     */
    private static ProcessedEvent createSampleEvent(String eventId, String database, 
                                                     String table, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", eventId);
        data.put("value", "sample-value");
        
        return ProcessedEvent.builder()
                .eventId(eventId)
                .eventType("INSERT")
                .database(database)
                .table(table)
                .timestamp(timestamp)
                .processTime(System.currentTimeMillis())
                .data(data)
                .partition("dt=" + (timestamp / 1000))
                .build();
    }

    /**
     * 配置说明
     */
    public static void printConfigurationGuide() {
        logger.info("=== Exactly-Once Configuration Guide ===");
        logger.info("");
        logger.info("To enable exactly-once semantics, you need:");
        logger.info("");
        logger.info("1. Configure Flink checkpointing mode:");
        logger.info("   flink.checkpointingMode: exactly-once");
        logger.info("");
        logger.info("2. Use idempotent sinks:");
        logger.info("   ExactlyOnceSinkBuilder.forFormat(format, config)");
        logger.info("       .withIdempotence(true)");
        logger.info("       .build()");
        logger.info("");
        logger.info("3. Ensure all events have unique IDs:");
        logger.info("   ProcessedEvent must have non-null eventId");
        logger.info("");
        logger.info("4. Configure appropriate state retention:");
        logger.info("   .withStateRetention(24 * 60 * 60 * 1000L) // 24 hours");
        logger.info("");
        logger.info("5. Optional: Enable dead letter queue for failed records:");
        logger.info("   .withDeadLetterQueue(\"/path/to/dlq\")");
        logger.info("");
        logger.info("Benefits of exactly-once semantics:");
        logger.info("- No data loss");
        logger.info("- No duplicate records in output");
        logger.info("- Consistent results even after failures");
        logger.info("");
        logger.info("Trade-offs:");
        logger.info("- Higher memory usage (state for event IDs)");
        logger.info("- Slightly higher checkpoint overhead");
        logger.info("- Requires unique event IDs");
    }
}
