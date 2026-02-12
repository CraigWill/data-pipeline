package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.util.MockStreamingRuntimeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等性文件Sink的单元测试
 * 
 * 验证需求:
 * - 9.2: 支持精确一次（Exactly-once）语义
 * - 9.3: 通过幂等性操作去重
 */
class IdempotentFileSinkTest {

    private MockSinkFunction mockSink;
    private IdempotentFileSink idempotentSink;

    @BeforeEach
    void setUp() throws Exception {
        mockSink = new MockSinkFunction();
        idempotentSink = new IdempotentFileSink(mockSink, 60000L); // 1分钟状态保留
        
        // 设置运行时上下文
        idempotentSink.setRuntimeContext(new MockStreamingRuntimeContext(false, 1, 0));
        idempotentSink.open(null);
    }

    /**
     * 测试基本的去重功能
     * 验证需求 9.3: 通过幂等性操作去重
     */
    @Test
    void testBasicDeduplication() throws Exception {
        // 创建测试事件
        ProcessedEvent event1 = createEvent("evt-001", "db1", "table1", 1000L);
        ProcessedEvent event2 = createEvent("evt-002", "db1", "table1", 2000L);
        ProcessedEvent event1Duplicate = createEvent("evt-001", "db1", "table1", 1000L);
        
        // 写入事件
        idempotentSink.invoke(event1, null);
        idempotentSink.invoke(event2, null);
        idempotentSink.invoke(event1Duplicate, null); // 重复事件
        
        // 验证只有唯一事件被写入
        assertEquals(2, mockSink.getWrittenEvents().size(), "Should write only unique events");
        assertEquals(3, idempotentSink.getTotalEvents(), "Should count all events");
        assertEquals(2, idempotentSink.getUniqueEvents(), "Should count unique events");
        assertEquals(1, idempotentSink.getDuplicateEvents(), "Should count duplicate events");
    }

    /**
     * 测试多个重复事件
     * 验证需求 9.3: 通过幂等性操作去重
     */
    @Test
    void testMultipleDuplicates() throws Exception {
        ProcessedEvent event = createEvent("evt-001", "db1", "table1", 1000L);
        
        // 写入同一事件5次
        for (int i = 0; i < 5; i++) {
            idempotentSink.invoke(event, null);
        }
        
        // 验证只写入一次
        assertEquals(1, mockSink.getWrittenEvents().size(), "Should write event only once");
        assertEquals(5, idempotentSink.getTotalEvents(), "Should count all attempts");
        assertEquals(1, idempotentSink.getUniqueEvents(), "Should have 1 unique event");
        assertEquals(4, idempotentSink.getDuplicateEvents(), "Should have 4 duplicates");
    }

    /**
     * 测试不同事件ID的处理
     * 验证需求 9.2: 支持精确一次语义
     */
    @Test
    void testDifferentEventIds() throws Exception {
        // 创建10个不同的事件
        for (int i = 0; i < 10; i++) {
            ProcessedEvent event = createEvent("evt-" + i, "db1", "table1", 1000L + i);
            idempotentSink.invoke(event, null);
        }
        
        // 验证所有事件都被写入
        assertEquals(10, mockSink.getWrittenEvents().size(), "Should write all unique events");
        assertEquals(10, idempotentSink.getUniqueEvents(), "Should have 10 unique events");
        assertEquals(0, idempotentSink.getDuplicateEvents(), "Should have no duplicates");
    }

    /**
     * 测试null事件ID的处理
     */
    @Test
    void testNullEventId() throws Exception {
        ProcessedEvent eventWithNullId = createEvent(null, "db1", "table1", 1000L);
        
        // null ID的事件应该被写入（无法去重）
        idempotentSink.invoke(eventWithNullId, null);
        idempotentSink.invoke(eventWithNullId, null);
        
        // 验证两次都被写入（因为无法去重）
        assertEquals(2, mockSink.getWrittenEvents().size(), 
                "Events with null ID should be written (cannot deduplicate)");
    }

    /**
     * 测试空事件ID的处理
     */
    @Test
    void testEmptyEventId() throws Exception {
        ProcessedEvent eventWithEmptyId = createEvent("", "db1", "table1", 1000L);
        
        // 空ID的事件应该被写入（无法去重）
        idempotentSink.invoke(eventWithEmptyId, null);
        idempotentSink.invoke(eventWithEmptyId, null);
        
        // 验证两次都被写入（因为无法去重）
        assertEquals(2, mockSink.getWrittenEvents().size(), 
                "Events with empty ID should be written (cannot deduplicate)");
    }

    /**
     * 测试null事件的处理
     */
    @Test
    void testNullEvent() throws Exception {
        // null事件应该被跳过
        idempotentSink.invoke(null, null);
        
        assertEquals(0, mockSink.getWrittenEvents().size(), "Null event should be skipped");
        assertEquals(0, idempotentSink.getTotalEvents(), "Should not count null events");
    }

    /**
     * 测试写入失败时不记录事件ID
     */
    @Test
    void testWriteFailureDoesNotRecordEventId() throws Exception {
        // 配置mock sink在第一次写入时失败
        mockSink.setFailOnNextWrite(true);
        
        ProcessedEvent event = createEvent("evt-001", "db1", "table1", 1000L);
        
        // 第一次写入应该失败
        assertThrows(Exception.class, () -> idempotentSink.invoke(event, null));
        
        // 验证事件ID没有被记录
        assertEquals(0, idempotentSink.getStateSize(), "Failed write should not record event ID");
        
        // 第二次写入应该成功（因为第一次失败，ID没有被记录）
        mockSink.setFailOnNextWrite(false);
        idempotentSink.invoke(event, null);
        
        assertEquals(1, mockSink.getWrittenEvents().size(), "Second attempt should succeed");
        assertEquals(1, idempotentSink.getStateSize(), "Successful write should record event ID");
    }

    /**
     * 测试状态大小跟踪
     */
    @Test
    void testStateSizeTracking() throws Exception {
        assertEquals(0, idempotentSink.getStateSize(), "Initial state should be empty");
        
        // 写入3个唯一事件
        idempotentSink.invoke(createEvent("evt-001", "db1", "table1", 1000L), null);
        idempotentSink.invoke(createEvent("evt-002", "db1", "table1", 2000L), null);
        idempotentSink.invoke(createEvent("evt-003", "db1", "table1", 3000L), null);
        
        assertEquals(3, idempotentSink.getStateSize(), "State should contain 3 event IDs");
        
        // 写入重复事件
        idempotentSink.invoke(createEvent("evt-001", "db1", "table1", 1000L), null);
        
        assertEquals(3, idempotentSink.getStateSize(), "State size should not change for duplicates");
    }

    /**
     * 测试统计信息
     */
    @Test
    void testStatistics() throws Exception {
        // 写入5个事件：3个唯一，2个重复
        idempotentSink.invoke(createEvent("evt-001", "db1", "table1", 1000L), null);
        idempotentSink.invoke(createEvent("evt-002", "db1", "table1", 2000L), null);
        idempotentSink.invoke(createEvent("evt-001", "db1", "table1", 1000L), null); // 重复
        idempotentSink.invoke(createEvent("evt-003", "db1", "table1", 3000L), null);
        idempotentSink.invoke(createEvent("evt-002", "db1", "table1", 2000L), null); // 重复
        
        assertEquals(5, idempotentSink.getTotalEvents(), "Total events should be 5");
        assertEquals(3, idempotentSink.getUniqueEvents(), "Unique events should be 3");
        assertEquals(2, idempotentSink.getDuplicateEvents(), "Duplicate events should be 2");
    }

    /**
     * 测试混合场景：唯一事件和重复事件
     * 验证需求 9.2 和 9.3
     */
    @Test
    void testMixedScenario() throws Exception {
        List<ProcessedEvent> events = new ArrayList<>();
        
        // 创建事件序列：1, 2, 1, 3, 2, 4, 1, 5
        events.add(createEvent("evt-001", "db1", "table1", 1000L));
        events.add(createEvent("evt-002", "db1", "table1", 2000L));
        events.add(createEvent("evt-001", "db1", "table1", 1000L)); // 重复
        events.add(createEvent("evt-003", "db1", "table1", 3000L));
        events.add(createEvent("evt-002", "db1", "table1", 2000L)); // 重复
        events.add(createEvent("evt-004", "db1", "table1", 4000L));
        events.add(createEvent("evt-001", "db1", "table1", 1000L)); // 重复
        events.add(createEvent("evt-005", "db1", "table1", 5000L));
        
        // 写入所有事件
        for (ProcessedEvent event : events) {
            idempotentSink.invoke(event, null);
        }
        
        // 验证结果
        assertEquals(8, idempotentSink.getTotalEvents(), "Total events should be 8");
        assertEquals(5, idempotentSink.getUniqueEvents(), "Unique events should be 5");
        assertEquals(3, idempotentSink.getDuplicateEvents(), "Duplicate events should be 3");
        assertEquals(5, mockSink.getWrittenEvents().size(), "Should write 5 unique events");
        
        // 验证写入的事件ID
        List<String> writtenIds = new ArrayList<>();
        for (ProcessedEvent event : mockSink.getWrittenEvents()) {
            writtenIds.add(event.getEventId());
        }
        
        assertTrue(writtenIds.contains("evt-001"), "Should contain evt-001");
        assertTrue(writtenIds.contains("evt-002"), "Should contain evt-002");
        assertTrue(writtenIds.contains("evt-003"), "Should contain evt-003");
        assertTrue(writtenIds.contains("evt-004"), "Should contain evt-004");
        assertTrue(writtenIds.contains("evt-005"), "Should contain evt-005");
    }

    /**
     * 创建测试事件
     */
    private ProcessedEvent createEvent(String eventId, String database, String table, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", eventId);
        data.put("value", "test-value");
        
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
     * Mock Sink实现，用于测试
     */
    private static class MockSinkFunction implements SinkFunction<ProcessedEvent> {
        private final List<ProcessedEvent> writtenEvents = new ArrayList<>();
        private boolean failOnNextWrite = false;

        @Override
        public void invoke(ProcessedEvent value, Context context) throws Exception {
            if (failOnNextWrite) {
                failOnNextWrite = false;
                throw new RuntimeException("Simulated write failure");
            }
            writtenEvents.add(value);
        }

        public List<ProcessedEvent> getWrittenEvents() {
            return writtenEvents;
        }

        public void setFailOnNextWrite(boolean fail) {
            this.failOnNextWrite = fail;
        }
    }
}
