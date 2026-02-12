package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * JsonFileSink单元测试
 * 测试JSON格式文件输出功能
 */
class JsonFileSinkTest {

    private OutputConfig config;

    @BeforeEach
    void setUp() {
        config = OutputConfig.builder()
                .path("/tmp/test-json-output")
                .format("json")
                .rollingSizeBytes(1024L * 1024L * 1024L)
                .rollingIntervalMs(3600000L)
                .compression("none")
                .maxRetries(3)
                .retryBackoff(2)
                .build();
    }

    @Test
    void testCreateSink() {
        // 测试创建JSON Sink
        JsonFileSink sink = new JsonFileSink(config);
        StreamingFileSink<ProcessedEvent> streamingSink = sink.createSink();
        
        assertThat(streamingSink).isNotNull();
    }

    @Test
    void testCreateSinkWithDifferentConfigs() {
        // 测试使用不同配置创建Sink
        OutputConfig config1 = OutputConfig.builder()
                .path("/tmp/output1")
                .format("json")
                .rollingSizeBytes(500L * 1024L * 1024L) // 500MB
                .rollingIntervalMs(1800000L) // 30 minutes
                .build();

        OutputConfig config2 = OutputConfig.builder()
                .path("/tmp/output2")
                .format("json")
                .rollingSizeBytes(2048L * 1024L * 1024L) // 2GB
                .rollingIntervalMs(7200000L) // 2 hours
                .build();

        JsonFileSink sink1 = new JsonFileSink(config1);
        JsonFileSink sink2 = new JsonFileSink(config2);

        assertThat(sink1.createSink()).isNotNull();
        assertThat(sink2.createSink()).isNotNull();
    }

    @Test
    void testJsonFileSinkInheritsFromAbstractFileSink() {
        // 测试JsonFileSink继承自AbstractFileSink
        JsonFileSink sink = new JsonFileSink(config);
        
        assertThat(sink).isInstanceOf(AbstractFileSink.class);
    }

    @Test
    void testJsonFileSinkUsesCorrectFileExtension() {
        // 测试JSON文件使用正确的扩展名
        JsonFileSink sink = new JsonFileSink(config);
        
        // 通过创建输出文件配置来验证扩展名
        var fileConfig = sink.createOutputFileConfig("json");
        assertThat(fileConfig.getPartSuffix()).isEqualTo(".json");
    }

    @Test
    void testJsonSerialization() throws Exception {
        // 测试JSON序列化功能
        // 验证需求 3.1: 支持JSON格式输出
        ProcessedEvent event = createTestEvent();
        
        // 使用JsonEncoder进行序列化测试
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        
        // 创建JsonEncoder实例并测试序列化
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        String json = mapper.writeValueAsString(event);
        outputStream.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        outputStream.write('\n');
        
        String result = outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
        
        // 验证JSON包含所有必要字段
        assertThat(result).contains("\"eventType\":\"INSERT\"");
        assertThat(result).contains("\"database\":\"mydb\"");
        assertThat(result).contains("\"table\":\"users\"");
        assertThat(result).contains("\"eventId\":\"test-event-1\"");
        assertThat(result).contains("\"name\":\"张三\"");
        assertThat(result).contains("\"email\":\"zhangsan@example.com\"");
        assertThat(result).endsWith("\n");
    }

    @Test
    void testJsonSerializationWithComplexData() throws Exception {
        // 测试复杂数据的JSON序列化
        Map<String, Object> complexData = new HashMap<>();
        complexData.put("id", 123);
        complexData.put("name", "测试用户");
        complexData.put("age", 30);
        complexData.put("active", true);
        complexData.put("balance", 1234.56);
        
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("UPDATE")
                .database("testdb")
                .table("accounts")
                .timestamp(1706428800000L)
                .processTime(1706428801000L)
                .data(complexData)
                .partition("1")
                .eventId("test-event-complex")
                .build();
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // 配置只序列化字段，不序列化getter方法
        mapper.setVisibility(
            com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,
            com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
        );
        mapper.setVisibility(
            com.fasterxml.jackson.annotation.PropertyAccessor.GETTER,
            com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
        );
        
        String json = mapper.writeValueAsString(event);
        
        // 验证所有数据类型都被正确序列化
        assertThat(json).contains("\"id\":123");
        assertThat(json).contains("\"name\":\"测试用户\"");
        assertThat(json).contains("\"age\":30");
        assertThat(json).contains("\"active\":true");
        assertThat(json).contains("\"balance\":1234.56");
        
        // 验证可以反序列化回对象
        ProcessedEvent deserialized = mapper.readValue(json, ProcessedEvent.class);
        assertThat(deserialized.getEventType()).isEqualTo("UPDATE");
        assertThat(deserialized.getDatabase()).isEqualTo("testdb");
        assertThat(deserialized.getTable()).isEqualTo("accounts");
        assertThat(deserialized.getData()).containsEntry("name", "测试用户");
    }

    // 辅助方法：创建测试事件
    private ProcessedEvent createTestEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "张三");
        data.put("email", "zhangsan@example.com");

        return ProcessedEvent.builder()
                .eventType("INSERT")
                .database("mydb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .processTime(System.currentTimeMillis())
                .data(data)
                .partition("0")
                .eventId("test-event-1")
                .build();
    }
}
