package com.realtime.pipeline.flink.processor;

import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EventProcessor基于属性的测试
 * 使用jqwik进行属性测试，验证通用正确性属性
 * 
 * Feature: realtime-data-pipeline
 */
class EventProcessorPropertyTest {

    private final EventProcessor processor = new EventProcessor();

    /**
     * Feature: realtime-data-pipeline, Property 7: 事件时间顺序保持
     * 
     * **Validates: Requirements 2.3, 9.4**
     * 
     * 对于任何事件序列，Flink处理后的输出应该保持原始的时间顺序
     * 
     * 测试策略:
     * - 生成具有递增时间戳的事件序列
     * - 处理所有事件
     * - 验证输出事件的时间戳保持递增顺序
     */
    @Property(tries = 20)
    void eventOrderPreservation(@ForAll("orderedEventSequences") List<ChangeEvent> events) throws Exception {
        // 处理所有事件
        List<ProcessedEvent> processedEvents = new ArrayList<>();
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                processedEvents.add(processed);
            }
        }

        // 验证时间顺序保持
        for (int i = 1; i < processedEvents.size(); i++) {
            long prevTimestamp = processedEvents.get(i - 1).getTimestamp();
            long currTimestamp = processedEvents.get(i).getTimestamp();
            
            assertTrue(currTimestamp >= prevTimestamp,
                    String.format("Event order not preserved: event[%d].timestamp=%d < event[%d].timestamp=%d",
                            i, currTimestamp, i - 1, prevTimestamp));
        }

        // 验证原始时间戳被保持（没有被修改）
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i).getTimestamp(), processedEvents.get(i).getTimestamp(),
                    "Original timestamp should be preserved");
        }
    }

    /**
     * Feature: realtime-data-pipeline, Property 39: 唯一标识符生成
     * 
     * **Validates: Requirements 9.6**
     * 
     * 对于任何处理的数据记录，系统应该为其生成全局唯一的标识符
     * 
     * 测试策略:
     * - 生成多个事件（eventId为null或空字符串）
     * - 处理所有事件
     * - 验证所有生成的eventId都是唯一的
     * 
     * 注意: 如果输入事件已有非空eventId，则保持不变。
     * 唯一性保证仅适用于EventProcessor生成的ID。
     */
    @Property(tries = 20)
    void uniqueIdentifierGeneration(@ForAll("eventsWithoutIds") List<ChangeEvent> events) throws Exception {
        // 处理所有事件
        List<ProcessedEvent> processedEvents = new ArrayList<>();
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                processedEvents.add(processed);
            }
        }

        // 收集所有eventId
        Set<String> eventIds = processedEvents.stream()
                .map(ProcessedEvent::getEventId)
                .collect(Collectors.toSet());

        // 验证所有eventId都存在且非空
        for (ProcessedEvent event : processedEvents) {
            assertNotNull(event.getEventId(), "Event ID should not be null");
            assertFalse(event.getEventId().isEmpty(), "Event ID should not be empty");
        }

        // 验证所有eventId都是唯一的
        assertEquals(processedEvents.size(), eventIds.size(),
                "All event IDs should be unique. Found " + (processedEvents.size() - eventIds.size()) + " duplicates");
    }

    /**
     * 属性测试: 处理时间戳总是大于或等于事件时间戳
     * 
     * 验证处理时间总是在事件时间之后或相同
     */
    @Property(tries = 20)
    void processTimeIsAfterEventTime(@ForAll("changeEvents") List<ChangeEvent> events) throws Exception {
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                assertTrue(processed.getProcessTime() >= processed.getTimestamp(),
                        "Process time should be >= event timestamp");
            }
        }
    }

    /**
     * 属性测试: 分区信息格式正确
     * 
     * 验证分区信息符合yyyyMMddHH格式
     */
    @Property(tries = 20)
    void partitionFormatIsCorrect(@ForAll("changeEvents") List<ChangeEvent> events) throws Exception {
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                assertNotNull(processed.getPartition(), "Partition should not be null");
                // 验证分区格式: yyyyMMddHH (10位数字)
                assertTrue(processed.getPartition().matches("\\d{10}"),
                        "Partition should match format yyyyMMddHH: " + processed.getPartition());
            }
        }
    }

    /**
     * 属性测试: 数据内容正确转换
     * 
     * 验证根据事件类型正确选择数据内容
     */
    @Property(tries = 20)
    void dataContentCorrectlyTransformed(@ForAll("changeEvents") List<ChangeEvent> events) throws Exception {
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                // 验证数据内容
                Map<String, Object> expectedData = event.getData();
                assertEquals(expectedData, processed.getData(),
                        "Data should be correctly transformed based on event type");

                // 验证事件类型保持
                assertEquals(event.getEventType(), processed.getEventType(),
                        "Event type should be preserved");

                // 验证数据库和表名保持
                assertEquals(event.getDatabase(), processed.getDatabase(),
                        "Database name should be preserved");
                assertEquals(event.getTable(), processed.getTable(),
                        "Table name should be preserved");
            }
        }
    }

    /**
     * 属性测试: 空事件处理
     * 
     * 验证null事件返回null
     */
    @Property(tries = 20)
    void nullEventReturnsNull() throws Exception {
        ProcessedEvent result = processor.map(null);
        assertNull(result, "Null event should return null");
    }

    // ==================== 数据生成器 ====================

    /**
     * 生成没有eventId的事件列表（用于测试唯一ID生成）
     */
    @Provide
    Arbitrary<List<ChangeEvent>> eventsWithoutIds() {
        return Combinators.combine(
                eventType(),
                databaseName(),
                tableName(),
                timestamp(),
                eventData()
        ).as((type, db, table, ts, data) -> {
            // 50%概率使用null，50%使用空字符串
            String eventId = Arbitraries.integers().between(0, 1).sample() == 0 ? null : "";
            
            if ("DELETE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, null, List.of("id"), eventId);
            } else if ("UPDATE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, data, List.of("id"), eventId);
            } else {
                return new ChangeEvent(type, db, table, ts, null, data, List.of("id"), eventId);
            }
        }).list().ofMinSize(5).ofMaxSize(50);
    }

    /**
     * 生成有序的事件序列（时间戳递增）
     */
    @Provide
    Arbitrary<List<ChangeEvent>> orderedEventSequences() {
        return Arbitraries.integers().between(5, 20).flatMap(size -> {
            long baseTimestamp = System.currentTimeMillis() - 3600000; // 1小时前
            
            return Arbitraries.integers().between(1000, 10000).list().ofSize(size)
                    .map(increments -> {
                        List<ChangeEvent> events = new ArrayList<>();
                        long currentTimestamp = baseTimestamp;
                        
                        for (int i = 0; i < size; i++) {
                            currentTimestamp += increments.get(i); // 递增时间戳
                            events.add(createChangeEvent(
                                    "testdb",
                                    "table" + (i % 3),
                                    currentTimestamp,
                                    "event-" + i
                            ));
                        }
                        
                        return events;
                    });
        });
    }

    /**
     * 生成变更事件列表
     */
    @Provide
    Arbitrary<List<ChangeEvent>> changeEvents() {
        return changeEvent().list().ofMinSize(5).ofMaxSize(50);
    }

    /**
     * 生成单个变更事件
     */
    @Provide
    Arbitrary<ChangeEvent> changeEvent() {
        return Combinators.combine(
                eventType(),
                databaseName(),
                tableName(),
                timestamp(),
                eventData(),
                eventId()
        ).as((type, db, table, ts, data, id) -> {
            if ("DELETE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, null, List.of("id"), id);
            } else if ("UPDATE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, data, List.of("id"), id);
            } else {
                return new ChangeEvent(type, db, table, ts, null, data, List.of("id"), id);
            }
        });
    }

    @Provide
    Arbitrary<String> eventType() {
        return Arbitraries.of("INSERT", "UPDATE", "DELETE");
    }

    @Provide
    Arbitrary<String> databaseName() {
        return Arbitraries.of("testdb", "proddb", "devdb");
    }

    @Provide
    Arbitrary<String> tableName() {
        return Arbitraries.of("users", "orders", "products", "customers");
    }

    @Provide
    Arbitrary<Long> timestamp() {
        long now = System.currentTimeMillis();
        return Arbitraries.longs().between(now - 86400000, now); // 过去24小时
    }

    @Provide
    Arbitrary<Map<String, Object>> eventData() {
        return Arbitraries.integers().between(1, 1000).map(id -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", "name_" + id);
            data.put("value", id * 100);
            return data;
        });
    }

    @Provide
    Arbitrary<String> eventId() {
        // 50%的概率返回null或空字符串，50%返回有效ID
        return Arbitraries.frequencyOf(
                Tuple.of(1, Arbitraries.just((String) null)),
                Tuple.of(1, Arbitraries.just("")),
                Tuple.of(3, Arbitraries.strings().alpha().ofLength(10))
        );
    }

    // ==================== 辅助方法 ====================

    private ChangeEvent createChangeEvent(String database, String table, long timestamp, String eventId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", UUID.randomUUID().toString());
        data.put("value", timestamp);

        return new ChangeEvent(
                "INSERT",
                database,
                table,
                timestamp,
                null,
                data,
                List.of("id"),
                eventId
        );
    }
}
