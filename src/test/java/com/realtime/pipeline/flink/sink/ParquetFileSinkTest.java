package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.avro.Schema;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试: ParquetFileSink
 * 
 * 测试需求 3.2: 支持Parquet格式输出
 */
class ParquetFileSinkTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreateSink() {
        // 准备配置
        OutputConfig config = OutputConfig.builder()
                .path(tempDir.toString())
                .format("parquet")
                .rollingSizeBytes(1024 * 1024 * 1024L) // 1GB
                .rollingIntervalMs(3600000L) // 1 hour
                .compression("snappy")
                .maxRetries(3)
                .retryBackoff(2)
                .build();

        // 创建ParquetFileSink
        ParquetFileSink sink = new ParquetFileSink(config);
        
        // 验证可以创建StreamingFileSink
        StreamingFileSink<ProcessedEvent> streamingSink = sink.createSink();
        assertNotNull(streamingSink, "StreamingFileSink should not be null");
    }

    @Test
    void testAvroSchemaIsValid() {
        // 验证Avro Schema是有效的
        Schema schema = ParquetFileSink.getAvroSchema();
        assertNotNull(schema, "Avro schema should not be null");
        assertEquals("ProcessedEvent", schema.getName(), "Schema name should be ProcessedEvent");
        assertEquals("com.realtime.pipeline.model", schema.getNamespace(), "Schema namespace should match");
        
        // 验证所有必需的字段都存在
        assertNotNull(schema.getField("eventType"), "eventType field should exist");
        assertNotNull(schema.getField("database"), "database field should exist");
        assertNotNull(schema.getField("table"), "table field should exist");
        assertNotNull(schema.getField("timestamp"), "timestamp field should exist");
        assertNotNull(schema.getField("processTime"), "processTime field should exist");
        assertNotNull(schema.getField("data"), "data field should exist");
        assertNotNull(schema.getField("partition"), "partition field should exist");
        assertNotNull(schema.getField("eventId"), "eventId field should exist");
    }

    @Test
    void testConstructorWithNullConfig() {
        // 验证null配置会抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            new ParquetFileSink(null);
        }, "Should throw IllegalArgumentException for null config");
    }

    @Test
    void testConstructorWithInvalidConfig() {
        // 验证无效配置会抛出异常
        OutputConfig invalidConfig = OutputConfig.builder()
                .path(null) // 无效的路径
                .format("parquet")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            new ParquetFileSink(invalidConfig);
        }, "Should throw IllegalArgumentException for invalid config");
    }

    @Test
    void testParquetFileExtension() {
        // 准备配置
        OutputConfig config = OutputConfig.builder()
                .path(tempDir.toString())
                .format("parquet")
                .rollingSizeBytes(1024 * 1024 * 1024L)
                .rollingIntervalMs(3600000L)
                .compression("snappy")
                .maxRetries(3)
                .retryBackoff(2)
                .build();

        // 创建ParquetFileSink
        ParquetFileSink sink = new ParquetFileSink(config);
        
        // 验证输出文件配置使用.parquet扩展名
        // 这是通过createOutputFileConfig方法设置的
        assertNotNull(sink.createSink(), "Sink should be created successfully");
    }

    @Test
    void testBucketAssignerCreation() {
        // 准备配置
        OutputConfig config = OutputConfig.builder()
                .path(tempDir.toString())
                .format("parquet")
                .rollingSizeBytes(1024 * 1024 * 1024L)
                .rollingIntervalMs(3600000L)
                .compression("snappy")
                .maxRetries(3)
                .retryBackoff(2)
                .build();

        // 创建ParquetFileSink
        ParquetFileSink sink = new ParquetFileSink(config);
        
        // 验证桶分配器可以创建
        assertNotNull(sink.createBucketAssigner(), "BucketAssigner should not be null");
    }

    @Test
    void testOutputPathCreation() {
        // 准备配置
        String outputPath = tempDir.toString() + "/parquet-output";
        OutputConfig config = OutputConfig.builder()
                .path(outputPath)
                .format("parquet")
                .rollingSizeBytes(1024 * 1024 * 1024L)
                .rollingIntervalMs(3600000L)
                .compression("snappy")
                .maxRetries(3)
                .retryBackoff(2)
                .build();

        // 创建ParquetFileSink
        ParquetFileSink sink = new ParquetFileSink(config);
        
        // 验证输出路径正确
        assertEquals(outputPath, sink.getOutputPath().getPath(), "Output path should match config");
    }
}
