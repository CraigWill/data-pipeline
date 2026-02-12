package com.realtime.pipeline.flink.source;

import com.realtime.pipeline.config.DataHubConfig;
import com.realtime.pipeline.model.ChangeEvent;
import org.apache.flink.configuration.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 单元测试 - DataHubSource
 * 
 * 测试DataHub数据源的核心功能:
 * - 配置验证
 * - 数据消费
 * - 反序列化
 * - 偏移量管理
 * - 错误处理
 */
class DataHubSourceTest {

    private DataHubConfig config;

    @BeforeEach
    void setUp() {
        config = DataHubConfig.builder()
            .endpoint("http://test-endpoint.com")
            .accessId("test-access-id")
            .accessKey("test-access-key")
            .project("test-project")
            .topic("test-topic")
            .consumerGroup("test-consumer-group")
            .startPosition("LATEST")
            .maxRetries(3)
            .retryBackoff(2)
            .build();
    }

    @Test
    void testConstructor() {
        // 测试构造函数
        DataHubSource source = new DataHubSource(config);
        
        assertThat(source).isNotNull();
        assertThat(source.getConsumerGroup()).isEqualTo("test-consumer-group");
        assertThat(source.getStartPosition()).isEqualTo("LATEST");
    }

    @Test
    void testOpenInitializesClient() throws Exception {
        // 测试open方法初始化客户端
        DataHubSource source = new DataHubSource(config);
        
        source.open(new Configuration());
        
        assertThat(source.isRunning()).isTrue();
        
        source.close();
    }

    @Test
    void testOpenWithInvalidConfigThrowsException() {
        // 测试无效配置抛出异常
        DataHubConfig invalidConfig = DataHubConfig.builder()
            .endpoint("")  // 无效的endpoint
            .accessId("test-access-id")
            .accessKey("test-access-key")
            .project("test-project")
            .topic("test-topic")
            .consumerGroup("test-consumer-group")
            .build();
        
        DataHubSource source = new DataHubSource(invalidConfig);
        
        assertThatThrownBy(() -> source.open(new Configuration()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endpoint");
    }

    @Test
    void testDeserializerFromBytes() throws Exception {
        // 测试从字节数组反序列化
        DataHubSource.ChangeEventDeserializer deserializer = 
            new DataHubSource.ChangeEventDeserializer();
        
        deserializer.open(null);
        
        String json = "{\"type\":\"INSERT\",\"database\":\"testdb\",\"table\":\"users\"," +
                     "\"timestamp\":1234567890,\"after\":{\"id\":1,\"name\":\"test\"}," +
                     "\"primaryKeys\":[\"id\"],\"eventId\":\"evt-123\"}";
        
        ChangeEvent event = deserializer.deserialize(json.getBytes());
        
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("INSERT");
        assertThat(event.getDatabase()).isEqualTo("testdb");
        assertThat(event.getTable()).isEqualTo("users");
        assertThat(event.getTimestamp()).isEqualTo(1234567890);
        assertThat(event.getEventId()).isEqualTo("evt-123");
    }

    @Test
    void testDeserializerFromMap() throws Exception {
        // 测试从Map反序列化
        DataHubSource.ChangeEventDeserializer deserializer = 
            new DataHubSource.ChangeEventDeserializer();
        
        Map<String, Object> record = new HashMap<>();
        record.put("type", "UPDATE");
        record.put("database", "testdb");
        record.put("table", "products");
        record.put("timestamp", 9876543210L);
        record.put("before", Map.of("id", 1, "price", 100));
        record.put("after", Map.of("id", 1, "price", 120));
        record.put("primaryKeys", List.of("id"));
        record.put("eventId", "evt-456");
        
        ChangeEvent event = deserializer.deserialize(record);
        
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("UPDATE");
        assertThat(event.getDatabase()).isEqualTo("testdb");
        assertThat(event.getTable()).isEqualTo("products");
        assertThat(event.getBefore()).containsEntry("price", 100);
        assertThat(event.getAfter()).containsEntry("price", 120);
    }

    @Test
    void testDeserializerIsNotEndOfStream() {
        // 测试流式数据源永不结束
        DataHubSource.ChangeEventDeserializer deserializer = 
            new DataHubSource.ChangeEventDeserializer();
        
        ChangeEvent event = ChangeEvent.builder()
            .eventType("INSERT")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .build();
        
        assertThat(deserializer.isEndOfStream(event)).isFalse();
    }

    @Test
    void testDeserializerProducedType() {
        // 测试生成的类型信息
        DataHubSource.ChangeEventDeserializer deserializer = 
            new DataHubSource.ChangeEventDeserializer();
        
        assertThat(deserializer.getProducedType()).isNotNull();
        assertThat(deserializer.getProducedType().getTypeClass()).isEqualTo(ChangeEvent.class);
    }

    @Test
    void testCancelStopsSource() throws Exception {
        // 测试cancel方法停止数据源
        DataHubSource source = new DataHubSource(config);
        source.open(new Configuration());
        
        assertThat(source.isRunning()).isTrue();
        
        source.cancel();
        
        assertThat(source.isRunning()).isFalse();
        
        source.close();
    }

    @Test
    void testCloseStopsSource() throws Exception {
        // 测试close方法停止数据源
        DataHubSource source = new DataHubSource(config);
        source.open(new Configuration());
        
        assertThat(source.isRunning()).isTrue();
        
        source.close();
        
        assertThat(source.isRunning()).isFalse();
    }

    @Test
    void testStartPositionConfiguration() {
        // 测试不同的起始位置配置
        DataHubConfig earliestConfig = DataHubConfig.builder()
            .endpoint("http://test-endpoint.com")
            .accessId("test-access-id")
            .accessKey("test-access-key")
            .project("test-project")
            .topic("test-topic")
            .consumerGroup("test-consumer-group")
            .startPosition("EARLIEST")
            .build();
        
        DataHubSource source = new DataHubSource(earliestConfig);
        assertThat(source.getStartPosition()).isEqualTo("EARLIEST");
    }

    @Test
    void testConsumerGroupConfiguration() {
        // 测试消费者组配置
        DataHubConfig customGroupConfig = DataHubConfig.builder()
            .endpoint("http://test-endpoint.com")
            .accessId("test-access-id")
            .accessKey("test-access-key")
            .project("test-project")
            .topic("test-topic")
            .consumerGroup("custom-group")
            .build();
        
        DataHubSource source = new DataHubSource(customGroupConfig);
        assertThat(source.getConsumerGroup()).isEqualTo("custom-group");
    }

    @Test
    void testDeserializerHandlesInvalidJson() {
        // 测试反序列化器处理无效JSON
        DataHubSource.ChangeEventDeserializer deserializer = 
            new DataHubSource.ChangeEventDeserializer();
        
        String invalidJson = "{invalid json}";
        
        assertThatThrownBy(() -> deserializer.deserialize(invalidJson.getBytes()))
            .isInstanceOf(Exception.class);
    }

    @Test
    void testDeserializerHandlesMissingFields() throws Exception {
        // 测试反序列化器处理缺失字段
        DataHubSource.ChangeEventDeserializer deserializer = 
            new DataHubSource.ChangeEventDeserializer();
        
        deserializer.open(null);
        
        // JSON缺少某些字段
        String json = "{\"type\":\"INSERT\",\"database\":\"testdb\",\"table\":\"users\"}";
        
        ChangeEvent event = deserializer.deserialize(json.getBytes());
        
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("INSERT");
        assertThat(event.getDatabase()).isEqualTo("testdb");
        assertThat(event.getTable()).isEqualTo("users");
    }

    @Test
    void testMultipleOpenCallsAreIdempotent() throws Exception {
        // 测试多次调用open是幂等的
        DataHubSource source = new DataHubSource(config);
        
        source.open(new Configuration());
        source.open(new Configuration());  // 第二次调用
        
        assertThat(source.isRunning()).isTrue();
        
        source.close();
    }

    @Test
    void testMultipleCloseCallsAreIdempotent() throws Exception {
        // 测试多次调用close是幂等的
        DataHubSource source = new DataHubSource(config);
        source.open(new Configuration());
        
        source.close();
        source.close();  // 第二次调用，不应抛出异常
        
        assertThat(source.isRunning()).isFalse();
    }
}
