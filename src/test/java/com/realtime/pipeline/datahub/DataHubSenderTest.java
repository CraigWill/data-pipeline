package com.realtime.pipeline.datahub;

import com.realtime.pipeline.config.DataHubConfig;
import com.realtime.pipeline.datahub.client.*;
import com.realtime.pipeline.model.ChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DataHubSender单元测试
 * 
 * 测试范围:
 * - 基本发送功能
 * - 批量发送功能
 * - 重试机制（需求1.5）
 * - 错误处理
 * - 指标统计
 */
class DataHubSenderTest {

    private DataHubConfig config;
    private DataHubClient mockClient;
    private DataHubSender sender;

    @BeforeEach
    void setUp() {
        // 创建测试配置
        config = DataHubConfig.builder()
            .endpoint("https://test.datahub.aliyuncs.com")
            .accessId("test-access-id")
            .accessKey("test-access-key")
            .project("test-project")
            .topic("test-topic")
            .consumerGroup("test-group")
            .maxRetries(3)
            .retryBackoff(2)
            .build();

        // 创建mock客户端
        mockClient = mock(DataHubClient.class);
        
        // 创建sender（使用mock客户端）
        sender = new DataHubSender(config, mockClient);
    }

    @Test
    void testSendSingleEvent_Success() throws Exception {
        // 准备测试数据
        ChangeEvent event = createTestEvent("INSERT", "users", 1);

        // 配置mock返回成功结果
        PutRecordsResult successResult = new PutRecordsResult();
        successResult.setFailedRecordCount(0);
        when(mockClient.putRecords(anyString(), anyString(), any())).thenReturn(successResult);

        // 执行发送
        sender.send(event);

        // 验证
        verify(mockClient, times(1)).putRecords(
            eq(config.getProject()),
            eq(config.getTopic()),
            any()
        );
        assertThat(sender.getRecordsSent()).isEqualTo(1);
        assertThat(sender.getRecordsFailed()).isEqualTo(0);
    }

    @Test
    void testSendSingleEvent_WithRetry() throws Exception {
        // 准备测试数据
        ChangeEvent event = createTestEvent("UPDATE", "orders", 2);

        // 配置mock：前2次失败，第3次成功
        PutRecordsResult failResult = new PutRecordsResult();
        failResult.setFailedRecordCount(1);
        failResult.addFailedRecord(new PutRecordsResult.FailedRecord(0, "NETWORK_ERROR", "Network timeout"));

        PutRecordsResult successResult = new PutRecordsResult();
        successResult.setFailedRecordCount(0);

        when(mockClient.putRecords(anyString(), anyString(), any()))
            .thenReturn(failResult)
            .thenReturn(failResult)
            .thenReturn(successResult);

        // 执行发送
        sender.send(event);

        // 验证重试了3次
        verify(mockClient, times(3)).putRecords(
            eq(config.getProject()),
            eq(config.getTopic()),
            any()
        );
        assertThat(sender.getRecordsSent()).isEqualTo(1);
        assertThat(sender.getRecordsFailed()).isEqualTo(0);
    }

    @Test
    void testSendSingleEvent_AllRetriesFailed() {
        // 准备测试数据
        ChangeEvent event = createTestEvent("DELETE", "products", 3);

        // 配置mock：所有尝试都失败
        PutRecordsResult failResult = new PutRecordsResult();
        failResult.setFailedRecordCount(1);
        failResult.addFailedRecord(new PutRecordsResult.FailedRecord(0, "SERVER_ERROR", "Internal server error"));

        when(mockClient.putRecords(anyString(), anyString(), any())).thenReturn(failResult);

        // 执行发送并验证抛出异常
        assertThatThrownBy(() -> sender.send(event))
            .isInstanceOf(DataHubSendException.class)
            .hasMessageContaining("Failed to send event to DataHub");

        // 验证重试了最大次数
        verify(mockClient, times(config.getMaxRetries())).putRecords(
            eq(config.getProject()),
            eq(config.getTopic()),
            any()
        );
        assertThat(sender.getRecordsSent()).isEqualTo(0);
        assertThat(sender.getRecordsFailed()).isEqualTo(1);
    }

    @Test
    void testSendBatch_Success() throws Exception {
        // 准备测试数据
        List<ChangeEvent> events = Arrays.asList(
            createTestEvent("INSERT", "users", 1),
            createTestEvent("UPDATE", "users", 2),
            createTestEvent("DELETE", "users", 3)
        );

        // 配置mock返回成功结果
        PutRecordsResult successResult = new PutRecordsResult();
        successResult.setFailedRecordCount(0);
        when(mockClient.putRecords(anyString(), anyString(), any())).thenReturn(successResult);

        // 执行批量发送
        sender.sendBatch(events);

        // 验证
        ArgumentCaptor<List<RecordEntry>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockClient, times(1)).putRecords(
            eq(config.getProject()),
            eq(config.getTopic()),
            recordsCaptor.capture()
        );
        
        List<RecordEntry> sentRecords = recordsCaptor.getValue();
        assertThat(sentRecords).hasSize(3);
        assertThat(sender.getRecordsSent()).isEqualTo(3);
        assertThat(sender.getRecordsFailed()).isEqualTo(0);
    }

    @Test
    void testSendBatch_EmptyList() throws Exception {
        // 发送空列表
        sender.sendBatch(new ArrayList<>());

        // 验证没有调用客户端
        verify(mockClient, never()).putRecords(anyString(), anyString(), any());
        assertThat(sender.getRecordsSent()).isEqualTo(0);
    }

    @Test
    void testSendBatch_NullList() throws Exception {
        // 发送null
        sender.sendBatch(null);

        // 验证没有调用客户端
        verify(mockClient, never()).putRecords(anyString(), anyString(), any());
        assertThat(sender.getRecordsSent()).isEqualTo(0);
    }

    @Test
    void testSend_NullEvent() {
        // 验证null事件抛出异常
        assertThatThrownBy(() -> sender.send(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ChangeEvent cannot be null");
    }

    @Test
    void testRecordEntryCreation() throws Exception {
        // 准备测试数据
        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("id", 1);
        beforeData.put("name", "old_name");

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);
        afterData.put("name", "new_name");

        ChangeEvent event = ChangeEvent.builder()
            .eventType("UPDATE")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .before(beforeData)
            .after(afterData)
            .primaryKeys(Arrays.asList("id"))
            .eventId(UUID.randomUUID().toString())
            .build();

        // 配置mock
        PutRecordsResult successResult = new PutRecordsResult();
        successResult.setFailedRecordCount(0);
        when(mockClient.putRecords(anyString(), anyString(), any())).thenReturn(successResult);

        // 执行发送
        sender.send(event);

        // 验证记录包含所有必要字段
        ArgumentCaptor<List<RecordEntry>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockClient).putRecords(anyString(), anyString(), recordsCaptor.capture());
        
        RecordEntry record = recordsCaptor.getValue().get(0);
        assertThat(record.getShardId()).isNotNull();
        assertThat(record.getData()).containsKeys("eventId", "eventType", "database", "table", "timestamp");
        assertThat(record.getData()).containsKeys("before", "after", "primaryKeys");
    }

    @Test
    void testTestConnection_Success() {
        // 配置mock返回主题信息
        TopicInfo topicInfo = new TopicInfo();
        topicInfo.setProjectName(config.getProject());
        topicInfo.setTopicName(config.getTopic());
        when(mockClient.getTopic(anyString(), anyString())).thenReturn(topicInfo);

        // 测试连接
        boolean result = sender.testConnection();

        // 验证
        assertThat(result).isTrue();
        verify(mockClient).getTopic(config.getProject(), config.getTopic());
    }

    @Test
    void testTestConnection_Failure() {
        // 配置mock抛出异常
        when(mockClient.getTopic(anyString(), anyString()))
            .thenThrow(new DataHubClientException("Connection failed"));

        // 测试连接
        boolean result = sender.testConnection();

        // 验证
        assertThat(result).isFalse();
    }

    @Test
    void testClose() {
        // 关闭sender
        sender.close();

        // 验证客户端被关闭
        verify(mockClient).close();
    }

    @Test
    void testGetMetrics() throws Exception {
        // 准备测试数据
        ChangeEvent event1 = createTestEvent("INSERT", "users", 1);
        ChangeEvent event2 = createTestEvent("UPDATE", "users", 2);

        // 配置mock
        PutRecordsResult successResult = new PutRecordsResult();
        successResult.setFailedRecordCount(0);
        when(mockClient.putRecords(anyString(), anyString(), any())).thenReturn(successResult);

        // 发送事件
        sender.send(event1);
        sender.send(event2);

        // 验证指标
        assertThat(sender.getRecordsSent()).isEqualTo(2);
        assertThat(sender.getRecordsFailed()).isEqualTo(0);
        assertThat(sender.getConfig()).isEqualTo(config);
    }

    /**
     * 创建测试用的变更事件
     */
    private ChangeEvent createTestEvent(String eventType, String table, int id) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", "test_name_" + id);
        data.put("email", "test" + id + "@example.com");

        return ChangeEvent.builder()
            .eventType(eventType)
            .database("testdb")
            .table(table)
            .timestamp(System.currentTimeMillis())
            .after(data)
            .primaryKeys(Arrays.asList("id"))
            .eventId(UUID.randomUUID().toString())
            .build();
    }
}
