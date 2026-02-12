package com.realtime.pipeline.consistency;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.flink.sink.ExactlyOnceSinkBuilder;
import com.realtime.pipeline.flink.sink.IdempotentFileSink;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.util.MockStreamingRuntimeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据一致性单元测试
 * 
 * 测试范围:
 * - 测试数据不丢失（至少一次语义）
 * - 测试幂等性操作（精确一次语义）
 * - 测试一致性检测
 * 
 * 验证需求: 9.1, 9.2, 9.3, 9.5
 */
class DataConsistencyTest {

    private ConsistencyValidator validator;
    private OutputConfig testConfig;

    @BeforeEach
    void setUp() {
        validator = new ConsistencyValidator();
        testConfig = createTestConfig();
    }

    // ========== 测试数据不丢失（需求 9.1: 至少一次语义）==========

    /**
     * 测试正常情况下数据不丢失
     * 验证需求 9.1: 至少一次语义
     */
    @Test
    void testNoDataLossInNormalCase() throws Exception {
        TrackingSinkFunction trackingSink = new TrackingSinkFunction();
        
        // 发送10个事件
        List<ProcessedEvent> events = createTestEvents(10);
        for (ProcessedEvent event : events) {
            trackingSink.invoke(event, null);
        }
        
        // 验证所有事件都被处理
        assertEquals(10, trackingSink.getProcessedCount(), 
                "All events should be processed");
        assertEquals(events.size(), trackingSink.getProcessedEventIds().size(),
                "No events should be lost");
    }

    /**
     * 测试重试后数据不丢失
     * 验证需求 9.1: 至少一次语义
     */
    @Test
    void testNoDataLossAfterRetry() throws Exception {
        RetryableSinkFunction retrySink = new RetryableSinkFunction(2); // 前2次失败
        
        ProcessedEvent event = createTestEvent("evt-001", 1000L);
        
        // 第一次尝试失败
        assertThrows(Exception.class, () -> retrySink.invoke(event, null));
        assertEquals(0, retrySink.getSuccessCount(), "First attempt should fail");
        
        // 第二次尝试失败
        assertThrows(Exception.class, () -> retrySink.invoke(event, null));
        assertEquals(0, retrySink.getSuccessCount(), "Second attempt should fail");
        
        // 第三次尝试成功
        retrySink.invoke(event, null);
        assertEquals(1, retrySink.getSuccessCount(), "Third attempt should succeed");
        assertTrue(retrySink.getProcessedEventIds().contains("evt-001"),
                "Event should be processed after retry");
    }

    /**
     * 测试多个事件的数据不丢失
     * 验证需求 9.1: 至少一次语义
     */
    @Test
    void testNoDataLossWithMultipleEvents() throws Exception {
        TrackingSinkFunction trackingSink = new TrackingSinkFunction();
        
        // 创建100个事件
        List<ProcessedEvent> events = createTestEvents(100);
        
        // 处理所有事件
        for (ProcessedEvent event : events) {
            trackingSink.invoke(event, null);
        }
        
        // 验证所有事件都被处理
        assertEquals(100, trackingSink.getProcessedCount(),
                "All 100 events should be processed");
        
        // 验证每个事件ID都被记录
        Set<String> processedIds = trackingSink.getProcessedEventIds();
        for (ProcessedEvent event : events) {
            assertTrue(processedIds.contains(event.getEventId()),
                    "Event " + event.getEventId() + " should be processed");
        }
    }

    /**
     * 测试部分失败情况下数据不丢失
     * 验证需求 9.1: 至少一次语义
     */
    @Test
    void testNoDataLossWithPartialFailures() throws Exception {
        SelectiveFailureSinkFunction selectiveSink = new SelectiveFailureSinkFunction(
                Set.of("evt-003", "evt-007")); // 这两个事件会失败
        
        List<ProcessedEvent> events = createTestEvents(10);
        int successCount = 0;
        int failureCount = 0;
        
        for (ProcessedEvent event : events) {
            try {
                selectiveSink.invoke(event, null);
                successCount++;
            } catch (Exception e) {
                failureCount++;
            }
        }
        
        // 验证成功和失败的数量
        assertEquals(8, successCount, "8 events should succeed");
        assertEquals(2, failureCount, "2 events should fail");
        assertEquals(8, selectiveSink.getSuccessCount(), 
                "8 events should be successfully processed");
        
        // 验证失败的事件可以重试
        for (ProcessedEvent event : events) {
            if (event.getEventId().equals("evt-003") || event.getEventId().equals("evt-007")) {
                // 重试失败的事件
                selectiveSink.clearFailureList();
                selectiveSink.invoke(event, null);
            }
        }
        
        assertEquals(10, selectiveSink.getSuccessCount(),
                "All events should be processed after retry");
    }

    // ========== 测试幂等性操作（需求 9.2, 9.3: 精确一次语义）==========

    /**
     * 测试基本的幂等性去重
     * 验证需求 9.3: 幂等性操作去重
     */
    @Test
    void testBasicIdempotence() throws Exception {
        TrackingSinkFunction baseSink = new TrackingSinkFunction();
        IdempotentFileSink idempotentSink = new IdempotentFileSink(baseSink, 60000L);
        idempotentSink.setRuntimeContext(new MockStreamingRuntimeContext(false, 1, 0));
        idempotentSink.open(null);
        
        ProcessedEvent event = createTestEvent("evt-001", 1000L);
        
        // 发送同一事件3次
        idempotentSink.invoke(event, null);
        idempotentSink.invoke(event, null);
        idempotentSink.invoke(event, null);
        
        // 验证只处理一次
        assertEquals(1, baseSink.getProcessedCount(),
                "Event should be processed only once");
        assertEquals(3, idempotentSink.getTotalEvents(),
                "Should count all invocations");
        assertEquals(1, idempotentSink.getUniqueEvents(),
                "Should have 1 unique event");
        assertEquals(2, idempotentSink.getDuplicateEvents(),
                "Should have 2 duplicates");
    }

    /**
     * 测试幂等性保证精确一次语义
     * 验证需求 9.2: 精确一次语义
     */
    @Test
    void testIdempotenceGuaranteesExactlyOnce() throws Exception {
        TrackingSinkFunction baseSink = new TrackingSinkFunction();
        IdempotentFileSink idempotentSink = new IdempotentFileSink(baseSink, 60000L);
        idempotentSink.setRuntimeContext(new MockStreamingRuntimeContext(false, 1, 0));
        idempotentSink.open(null);
        
        // 创建10个事件，每个发送多次
        for (int i = 1; i <= 10; i++) {
            ProcessedEvent event = createTestEvent("evt-" + String.format("%03d", i), 1000L * i);
            
            // 每个事件发送随机次数（1-5次）
            int repeatCount = 1 + (i % 5);
            for (int j = 0; j < repeatCount; j++) {
                idempotentSink.invoke(event, null);
            }
        }
        
        // 验证每个事件只被处理一次
        assertEquals(10, baseSink.getProcessedCount(),
                "Each event should be processed exactly once");
        assertEquals(10, idempotentSink.getUniqueEvents(),
                "Should have 10 unique events");
        
        // 验证所有事件ID都被处理
        Set<String> processedIds = baseSink.getProcessedEventIds();
        for (int i = 1; i <= 10; i++) {
            String eventId = "evt-" + String.format("%03d", i);
            assertTrue(processedIds.contains(eventId),
                    "Event " + eventId + " should be processed");
        }
    }

    /**
     * 测试幂等性在失败重试场景下的表现
     * 验证需求 9.2 和 9.3
     */
    @Test
    void testIdempotenceWithRetries() throws Exception {
        RetryableSinkFunction retrySink = new RetryableSinkFunction(1); // 第一次失败
        IdempotentFileSink idempotentSink = new IdempotentFileSink(retrySink, 60000L);
        idempotentSink.setRuntimeContext(new MockStreamingRuntimeContext(false, 1, 0));
        idempotentSink.open(null);
        
        ProcessedEvent event = createTestEvent("evt-001", 1000L);
        
        // 第一次尝试会失败
        assertThrows(Exception.class, () -> idempotentSink.invoke(event, null));
        assertEquals(0, retrySink.getSuccessCount(), "First attempt should fail");
        assertEquals(0, idempotentSink.getUniqueEvents(), 
                "Failed write should not count as unique");
        
        // 第二次尝试成功
        idempotentSink.invoke(event, null);
        assertEquals(1, retrySink.getSuccessCount(), "Second attempt should succeed");
        assertEquals(1, idempotentSink.getUniqueEvents(),
                "Successful write should count as unique");
        
        // 第三次尝试（重复）
        idempotentSink.invoke(event, null);
        assertEquals(1, retrySink.getSuccessCount(), 
                "Duplicate should not be written");
        assertEquals(1, idempotentSink.getDuplicateEvents(),
                "Should detect duplicate");
    }

    /**
     * 测试幂等性操作产生相同结果
     * 验证需求 9.3: 幂等性操作去重
     */
    @Test
    void testIdempotentOperationsProduceSameResult() throws Exception {
        TrackingSinkFunction baseSink = new TrackingSinkFunction();
        IdempotentFileSink idempotentSink = new IdempotentFileSink(baseSink, 60000L);
        idempotentSink.setRuntimeContext(new MockStreamingRuntimeContext(false, 1, 0));
        idempotentSink.open(null);
        
        ProcessedEvent event = createTestEvent("evt-001", 1000L);
        
        // 第一次处理
        idempotentSink.invoke(event, null);
        int stateSize1 = idempotentSink.getStateSize();
        long uniqueEvents1 = idempotentSink.getUniqueEvents();
        
        // 第二次处理（重复）
        idempotentSink.invoke(event, null);
        int stateSize2 = idempotentSink.getStateSize();
        long uniqueEvents2 = idempotentSink.getUniqueEvents();
        
        // 第三次处理（重复）
        idempotentSink.invoke(event, null);
        int stateSize3 = idempotentSink.getStateSize();
        long uniqueEvents3 = idempotentSink.getUniqueEvents();
        
        // 验证状态保持不变
        assertEquals(stateSize1, stateSize2, "State size should not change");
        assertEquals(stateSize2, stateSize3, "State size should not change");
        assertEquals(uniqueEvents1, uniqueEvents2, "Unique count should not change");
        assertEquals(uniqueEvents2, uniqueEvents3, "Unique count should not change");
        
        // 验证只写入一次
        assertEquals(1, baseSink.getProcessedCount(),
                "Event should be written only once");
    }

    /**
     * 测试ExactlyOnceSinkBuilder创建的Sink的幂等性
     * 验证需求 9.2 和 9.3
     */
    @Test
    void testExactlyOnceSinkBuilderIdempotence() {
        SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
                .forFormat("csv", testConfig)
                .withIdempotence(true)
                .build();
        
        assertNotNull(sink, "Sink should be created");
        assertTrue(sink instanceof IdempotentFileSink,
                "Sink should be IdempotentFileSink");
    }

    // ========== 测试一致性检测（需求 9.5）==========

    /**
     * 测试检测INSERT事件的数据不一致
     * 验证需求 9.5: 数据不一致检测
     */
    @Test
    void testDetectInsertEventInconsistency() {
        // 缺少after数据的INSERT事件
        ChangeEvent invalidInsert = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(null) // 缺少after数据
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();
        
        ValidationResult result = validator.validateChangeEvent(invalidInsert);
        
        assertFalse(result.isValid(), "Invalid INSERT should be detected");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("INSERT event must have 'after' data")),
                "Should detect missing 'after' data");
    }

    /**
     * 测试检测UPDATE事件的数据不一致
     * 验证需求 9.5: 数据不一致检测
     */
    @Test
    void testDetectUpdateEventInconsistency() {
        // 缺少before数据的UPDATE事件
        ChangeEvent invalidUpdate = ChangeEvent.builder()
                .eventType("UPDATE")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .before(null) // 缺少before数据
                .after(Map.of("id", 1, "name", "Bob"))
                .primaryKeys(List.of("id"))
                .eventId("evt-002")
                .build();
        
        ValidationResult result = validator.validateChangeEvent(invalidUpdate);
        
        assertFalse(result.isValid(), "Invalid UPDATE should be detected");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("UPDATE event must have 'before' data")),
                "Should detect missing 'before' data");
    }

    /**
     * 测试检测DELETE事件的数据不一致
     * 验证需求 9.5: 数据不一致检测
     */
    @Test
    void testDetectDeleteEventInconsistency() {
        // 缺少before数据的DELETE事件
        ChangeEvent invalidDelete = ChangeEvent.builder()
                .eventType("DELETE")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .before(null) // 缺少before数据
                .primaryKeys(List.of("id"))
                .eventId("evt-003")
                .build();
        
        ValidationResult result = validator.validateChangeEvent(invalidDelete);
        
        assertFalse(result.isValid(), "Invalid DELETE should be detected");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("DELETE event must have 'before' data")),
                "Should detect missing 'before' data");
    }

    /**
     * 测试检测主键不一致
     * 验证需求 9.5: 数据不一致检测
     */
    @Test
    void testDetectPrimaryKeyInconsistency() {
        // 主键不在数据中
        ChangeEvent eventWithMissingPK = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("name", "Alice")) // 缺少id字段
                .primaryKeys(List.of("id"))
                .eventId("evt-004")
                .build();
        
        ValidationResult result = validator.validateChangeEvent(eventWithMissingPK);
        
        assertFalse(result.isValid(), "Missing primary key should be detected");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Primary key 'id' not found in data")),
                "Should detect missing primary key in data");
    }

    /**
     * 测试检测事件间的不一致
     * 验证需求 9.5: 数据不一致检测
     */
    @Test
    void testDetectEventConsistency() {
        long timestamp = System.currentTimeMillis();
        
        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(Map.of("id", 1, "name", "Alice"))
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();
        
        // ProcessedEvent的主键值不一致
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(Map.of("id", 2, "name", "Alice")) // id不一致
                .partition("users")
                .eventId("evt-001")
                .build();
        
        ValidationResult result = validator.validateEventConsistency(changeEvent, processedEvent);
        
        assertFalse(result.isValid(), "Primary key mismatch should be detected");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Primary key 'id' value mismatch")),
                "Should detect primary key value mismatch");
    }

    /**
     * 测试检测ProcessedEvent的时间不一致
     * 验证需求 9.5: 数据不一致检测
     */
    @Test
    void testDetectProcessedEventTimeInconsistency() {
        long timestamp = System.currentTimeMillis();
        
        // processTime早于timestamp
        ProcessedEvent invalidEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp - 1000) // 处理时间早于事件时间
                .data(Map.of("id", 1, "name", "Alice"))
                .partition("users")
                .eventId("evt-001")
                .build();
        
        ValidationResult result = validator.validateProcessedEvent(invalidEvent);
        
        assertFalse(result.isValid(), "Time inconsistency should be detected");
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Process time") && e.contains("before event timestamp")),
                "Should detect process time before event timestamp");
    }

    /**
     * 测试检测多个不一致问题
     * 验证需求 9.5: 数据不一致检测
     */
    @Test
    void testDetectMultipleInconsistencies() {
        // 创建有多个问题的事件
        ChangeEvent invalidEvent = ChangeEvent.builder()
                .eventType("INVALID_TYPE") // 无效的事件类型
                .database("") // 空数据库名
                .table(null) // null表名
                .timestamp(0) // 无效时间戳
                .after(null) // 缺少数据
                .primaryKeys(null) // 缺少主键
                .eventId("") // 空事件ID
                .build();
        
        ValidationResult result = validator.validateChangeEvent(invalidEvent);
        
        assertFalse(result.isValid(), "Multiple inconsistencies should be detected");
        assertTrue(result.getErrors().size() >= 5,
                "Should detect multiple errors");
    }

    /**
     * 测试一致性检测不影响有效事件
     * 验证需求 9.5: 数据不一致检测
     */
    @Test
    void testConsistencyCheckDoesNotAffectValidEvents() {
        // 创建有效的INSERT事件
        ChangeEvent validInsert = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("id", 1, "name", "Alice"))
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();
        
        ValidationResult result = validator.validateChangeEvent(validInsert);
        
        assertTrue(result.isValid(), "Valid event should pass validation");
        assertTrue(result.getErrors().isEmpty(), "Should have no errors");
    }

    // ========== 辅助方法 ==========

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

    /**
     * 创建测试事件
     */
    private ProcessedEvent createTestEvent(String eventId, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", eventId);
        data.put("value", "test-value-" + eventId);
        
        return ProcessedEvent.builder()
                .eventId(eventId)
                .eventType("INSERT")
                .database("testdb")
                .table("test_table")
                .timestamp(timestamp)
                .processTime(System.currentTimeMillis())
                .data(data)
                .partition("dt=" + (timestamp / 1000))
                .build();
    }

    /**
     * 创建多个测试事件
     */
    private List<ProcessedEvent> createTestEvents(int count) {
        List<ProcessedEvent> events = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String eventId = "evt-" + String.format("%03d", i);
            events.add(createTestEvent(eventId, 1000L * i));
        }
        return events;
    }

    // ========== 测试用的Sink实现 ==========

    /**
     * 跟踪处理事件的Sink
     */
    private static class TrackingSinkFunction implements SinkFunction<ProcessedEvent> {
        private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
        private final AtomicInteger processedCount = new AtomicInteger(0);

        @Override
        public void invoke(ProcessedEvent value, Context context) {
            processedEventIds.add(value.getEventId());
            processedCount.incrementAndGet();
        }

        public Set<String> getProcessedEventIds() {
            return new HashSet<>(processedEventIds);
        }

        public int getProcessedCount() {
            return processedCount.get();
        }
    }

    /**
     * 可重试的Sink（前N次失败）
     */
    private static class RetryableSinkFunction implements SinkFunction<ProcessedEvent> {
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final int failureCount;
        private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
        private final AtomicInteger successCount = new AtomicInteger(0);

        public RetryableSinkFunction(int failureCount) {
            this.failureCount = failureCount;
        }

        @Override
        public void invoke(ProcessedEvent value, Context context) throws Exception {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= failureCount) {
                throw new RuntimeException("Simulated failure (attempt " + attempt + ")");
            }
            processedEventIds.add(value.getEventId());
            successCount.incrementAndGet();
        }

        public Set<String> getProcessedEventIds() {
            return new HashSet<>(processedEventIds);
        }

        public int getSuccessCount() {
            return successCount.get();
        }
    }

    /**
     * 选择性失败的Sink（特定事件ID会失败）
     */
    private static class SelectiveFailureSinkFunction implements SinkFunction<ProcessedEvent> {
        private Set<String> failureEventIds;
        private final AtomicInteger successCount = new AtomicInteger(0);

        public SelectiveFailureSinkFunction(Set<String> failureEventIds) {
            this.failureEventIds = new HashSet<>(failureEventIds);
        }

        @Override
        public void invoke(ProcessedEvent value, Context context) throws Exception {
            if (failureEventIds.contains(value.getEventId())) {
                throw new RuntimeException("Simulated failure for event: " + value.getEventId());
            }
            successCount.incrementAndGet();
        }

        public int getSuccessCount() {
            return successCount.get();
        }

        public void clearFailureList() {
            failureEventIds.clear();
        }
    }
}
