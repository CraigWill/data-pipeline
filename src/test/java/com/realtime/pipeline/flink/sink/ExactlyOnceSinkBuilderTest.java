package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExactlyOnceSinkBuilder的单元测试
 * 
 * 验证需求:
 * - 9.2: 支持精确一次（Exactly-once）语义
 * - 9.3: 通过幂等性操作去重
 */
class ExactlyOnceSinkBuilderTest {

    /**
     * 测试创建基本的Sink
     */
    @Test
    void testCreateBasicSink() {
        OutputConfig config = createTestConfig();
        
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .build();
        
        assertNotNull(sink, "Sink should be created");
    }

    /**
     * 测试创建带幂等性的Sink
     * 验证需求 9.2 和 9.3
     */
    @Test
    void testCreateIdempotentSink() {
        OutputConfig config = createTestConfig();
        
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .withIdempotence(true)
                .build();
        
        assertNotNull(sink, "Idempotent sink should be created");
        assertTrue(sink instanceof IdempotentFileSink, 
                "Sink should be wrapped with IdempotentFileSink");
    }

    /**
     * 测试创建带死信队列的Sink
     */
    @Test
    void testCreateSinkWithDLQ() {
        OutputConfig config = createTestConfig();
        
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .withDeadLetterQueue("/test/dlq")
                .build();
        
        assertNotNull(sink, "Sink with DLQ should be created");
        assertTrue(sink instanceof FileSinkWithDLQ, 
                "Sink should be wrapped with FileSinkWithDLQ");
    }

    /**
     * 测试创建完整配置的Sink（幂等性 + 死信队列）
     * 验证需求 9.2 和 9.3
     */
    @Test
    void testCreateFullyConfiguredSink() {
        OutputConfig config = createTestConfig();
        
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .withIdempotence(true)
                .withStateRetention(3600000L) // 1小时
                .withDeadLetterQueue("/test/dlq")
                .build();
        
        assertNotNull(sink, "Fully configured sink should be created");
        
        // 最外层应该是FileSinkWithDLQ
        assertTrue(sink instanceof FileSinkWithDLQ, 
                "Outer wrapper should be FileSinkWithDLQ");
    }

    /**
     * 测试快捷方法：创建精确一次语义的Sink
     */
    @Test
    void testCreateExactlyOnceSinkShortcut() {
        OutputConfig config = createTestConfig();
        
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder.createExactlyOnceSink(
                "csv", config, "/test/dlq");
        
        assertNotNull(sink, "Exactly-once sink should be created");
        assertTrue(sink instanceof FileSinkWithDLQ, 
                "Sink should be wrapped with FileSinkWithDLQ");
    }

    /**
     * 测试快捷方法：创建至少一次语义的Sink
     */
    @Test
    void testCreateAtLeastOnceSinkShortcut() {
        OutputConfig config = createTestConfig();
        
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder.createAtLeastOnceSink(
                "csv", config, "/test/dlq");
        
        assertNotNull(sink, "At-least-once sink should be created");
        assertTrue(sink instanceof FileSinkWithDLQ, 
                "Sink should be wrapped with FileSinkWithDLQ");
    }

    /**
     * 测试支持的格式
     */
    @Test
    void testSupportedFormats() {
        OutputConfig config = createTestConfig();
        
        // CSV格式
        SinkFunction<ProcessedEvent> csvSink = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .build();
        assertNotNull(csvSink, "CSV sink should be created");
        
        // Parquet格式
        SinkFunction<ProcessedEvent> parquetSink = ExactlyOnceSinkBuilder
                .forFormat("parquet", config)
                .build();
        assertNotNull(parquetSink, "Parquet sink should be created");
        
        // JSON格式（可能不存在，会fallback到CSV）
        SinkFunction<ProcessedEvent> jsonSink = ExactlyOnceSinkBuilder
                .forFormat("json", config)
                .build();
        assertNotNull(jsonSink, "JSON sink should be created (or fallback to CSV)");
    }

    /**
     * 测试不支持的格式
     */
    @Test
    void testUnsupportedFormat() {
        OutputConfig config = createTestConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            ExactlyOnceSinkBuilder
                    .forFormat("xml", config)
                    .build();
        }, "Should throw exception for unsupported format");
    }

    /**
     * 测试null格式
     */
    @Test
    void testNullFormat() {
        OutputConfig config = createTestConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            ExactlyOnceSinkBuilder.forFormat(null, config);
        }, "Should throw exception for null format");
    }

    /**
     * 测试空格式
     */
    @Test
    void testEmptyFormat() {
        OutputConfig config = createTestConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            ExactlyOnceSinkBuilder.forFormat("", config);
        }, "Should throw exception for empty format");
    }

    /**
     * 测试null配置
     */
    @Test
    void testNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> {
            ExactlyOnceSinkBuilder.forFormat("csv", null);
        }, "Should throw exception for null config");
    }

    /**
     * 测试null DLQ路径
     */
    @Test
    void testNullDLQPath() {
        OutputConfig config = createTestConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            ExactlyOnceSinkBuilder
                    .forFormat("csv", config)
                    .withDeadLetterQueue(null);
        }, "Should throw exception for null DLQ path");
    }

    /**
     * 测试空DLQ路径
     */
    @Test
    void testEmptyDLQPath() {
        OutputConfig config = createTestConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            ExactlyOnceSinkBuilder
                    .forFormat("csv", config)
                    .withDeadLetterQueue("");
        }, "Should throw exception for empty DLQ path");
    }

    /**
     * 测试无效的状态保留时间
     */
    @Test
    void testInvalidStateRetention() {
        OutputConfig config = createTestConfig();
        
        assertThrows(IllegalArgumentException.class, () -> {
            ExactlyOnceSinkBuilder
                    .forFormat("csv", config)
                    .withStateRetention(0);
        }, "Should throw exception for zero state retention");
        
        assertThrows(IllegalArgumentException.class, () -> {
            ExactlyOnceSinkBuilder
                    .forFormat("csv", config)
                    .withStateRetention(-1000);
        }, "Should throw exception for negative state retention");
    }

    /**
     * 测试有效的状态保留时间
     */
    @Test
    void testValidStateRetention() {
        OutputConfig config = createTestConfig();
        
        // 1小时
        SinkFunction<ProcessedEvent> sink1 = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .withIdempotence(true)
                .withStateRetention(3600000L)
                .build();
        assertNotNull(sink1, "Sink with 1 hour retention should be created");
        
        // 24小时
        SinkFunction<ProcessedEvent> sink2 = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .withIdempotence(true)
                .withStateRetention(86400000L)
                .build();
        assertNotNull(sink2, "Sink with 24 hour retention should be created");
    }

    /**
     * 测试构建器的链式调用
     */
    @Test
    void testBuilderChaining() {
        OutputConfig config = createTestConfig();
        
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .withIdempotence(true)
                .withStateRetention(7200000L)
                .withDeadLetterQueue("/test/dlq")
                .build();
        
        assertNotNull(sink, "Chained builder should create sink");
    }

    /**
     * 测试格式大小写不敏感
     */
    @Test
    void testFormatCaseInsensitive() {
        OutputConfig config = createTestConfig();
        
        SinkFunction<ProcessedEvent> sink1 = ExactlyOnceSinkBuilder
                .forFormat("CSV", config)
                .build();
        assertNotNull(sink1, "Uppercase format should work");
        
        SinkFunction<ProcessedEvent> sink2 = ExactlyOnceSinkBuilder
                .forFormat("Csv", config)
                .build();
        assertNotNull(sink2, "Mixed case format should work");
        
        SinkFunction<ProcessedEvent> sink3 = ExactlyOnceSinkBuilder
                .forFormat("csv", config)
                .build();
        assertNotNull(sink3, "Lowercase format should work");
    }

    /**
     * 创建测试配置
     */
    private OutputConfig createTestConfig() {
        return OutputConfig.builder()
                .path("/test/output")
                .format("csv")
                .rollingSizeBytes(1024 * 1024 * 1024L)
                .rollingIntervalMs(3600000L)
                .compression("none")
                .maxRetries(3)
                .retryBackoff(2)
                .build();
    }
}
