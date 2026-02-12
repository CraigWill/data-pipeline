package com.realtime.pipeline.flink.processor;

import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EventProcessor单元测试
 * 测试事件处理器的核心功能
 */
class EventProcessorTest {

    private EventProcessor processor;
    private static final DateTimeFormatter PARTITION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @BeforeEach
    void setUp() {
        processor = new EventProcessor();
    }

    @Test
    void testProcessInsertEvent() throws Exception {
        // 准备INSERT事件
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);
        afterData.put("name", "张三");
        afterData.put("email", "zhangsan@example.com");

        long timestamp = System.currentTimeMillis();
        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(afterData)
                .primaryKeys(List.of("id"))
                .eventId("test-event-1")
                .build();

        // 处理事件
        ProcessedEvent result = processor.map(changeEvent);

        // 验证结果
        assertNotNull(result);
        assertEquals("INSERT", result.getEventType());
        assertEquals("testdb", result.getDatabase());
        assertEquals("users", result.getTable());
        assertEquals(timestamp, result.getTimestamp());
        assertEquals(afterData, result.getData());
        assertEquals("test-event-1", result.getEventId());
        assertNotNull(result.getPartition());
        assertTrue(result.getProcessTime() >= timestamp);
    }

    @Test
    void testProcessUpdateEvent() throws Exception {
        // 准备UPDATE事件
        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("id", 1);
        beforeData.put("name", "张三");
        beforeData.put("email", "old@example.com");

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);
        afterData.put("name", "张三");
        afterData.put("email", "new@example.com");

        long timestamp = System.currentTimeMillis();
        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("UPDATE")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .before(beforeData)
                .after(afterData)
                .primaryKeys(List.of("id"))
                .eventId("test-event-2")
                .build();

        // 处理事件
        ProcessedEvent result = processor.map(changeEvent);

        // 验证结果 - UPDATE事件应该使用after数据
        assertNotNull(result);
        assertEquals("UPDATE", result.getEventType());
        assertEquals(afterData, result.getData());
        assertEquals("test-event-2", result.getEventId());
    }

    @Test
    void testProcessDeleteEvent() throws Exception {
        // 准备DELETE事件
        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("id", 1);
        beforeData.put("name", "张三");
        beforeData.put("email", "zhangsan@example.com");

        long timestamp = System.currentTimeMillis();
        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("DELETE")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .before(beforeData)
                .primaryKeys(List.of("id"))
                .eventId("test-event-3")
                .build();

        // 处理事件
        ProcessedEvent result = processor.map(changeEvent);

        // 验证结果 - DELETE事件应该使用before数据
        assertNotNull(result);
        assertEquals("DELETE", result.getEventType());
        assertEquals(beforeData, result.getData());
        assertEquals("test-event-3", result.getEventId());
    }

    @Test
    void testGenerateEventIdWhenMissing() throws Exception {
        // 准备没有eventId的事件
        // 注意: ChangeEvent构造器会自动生成UUID如果eventId为null
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);

        long timestamp = System.currentTimeMillis();
        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(afterData)
                .primaryKeys(List.of("id"))
                .eventId(null)  // 没有eventId，ChangeEvent会生成UUID
                .build();

        // 处理事件
        ProcessedEvent result = processor.map(changeEvent);

        // 验证有eventId（由ChangeEvent生成的UUID）
        assertNotNull(result);
        assertNotNull(result.getEventId());
        assertFalse(result.getEventId().isEmpty());
        // ChangeEvent生成的是标准UUID格式
        assertTrue(result.getEventId().contains("-"));
    }

    @Test
    void testGenerateEventIdWhenEmpty() throws Exception {
        // 准备eventId为空字符串的事件
        // EventProcessor应该为空字符串生成新的eventId
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);

        long timestamp = System.currentTimeMillis();
        
        // 直接创建ChangeEvent对象，绕过builder以设置空字符串
        ChangeEvent changeEvent = new ChangeEvent(
                "INSERT",
                "testdb",
                "users",
                timestamp,
                null,
                afterData,
                List.of("id"),
                ""  // 空eventId
        );

        // 处理事件
        ProcessedEvent result = processor.map(changeEvent);

        // 验证生成了新的eventId
        assertNotNull(result);
        assertNotNull(result.getEventId());
        assertFalse(result.getEventId().isEmpty());
        // 验证eventId格式: {database}_{table}_{timestamp}_{uuid}
        assertTrue(result.getEventId().startsWith("testdb_users_" + timestamp));
    }

    @Test
    void testPartitionGeneration() throws Exception {
        // 准备特定时间戳的事件
        // 2025-01-28 15:30:00
        LocalDateTime dateTime = LocalDateTime.of(2025, 1, 28, 15, 30, 0);
        long timestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(afterData)
                .primaryKeys(List.of("id"))
                .eventId("test-event-4")
                .build();

        // 处理事件
        ProcessedEvent result = processor.map(changeEvent);

        // 验证分区格式: yyyyMMddHH
        assertNotNull(result);
        assertNotNull(result.getPartition());
        assertEquals("2025012815", result.getPartition());
    }

    @Test
    void testTimestampPreservation() throws Exception {
        // 准备事件
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);

        long originalTimestamp = System.currentTimeMillis() - 10000; // 10秒前
        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(originalTimestamp)
                .after(afterData)
                .primaryKeys(List.of("id"))
                .eventId("test-event-5")
                .build();

        // 处理事件
        ProcessedEvent result = processor.map(changeEvent);

        // 验证保持了原始时间戳
        assertNotNull(result);
        assertEquals(originalTimestamp, result.getTimestamp());
        // 验证processTime是当前时间
        assertTrue(result.getProcessTime() > originalTimestamp);
        assertTrue(result.getProcessTime() <= System.currentTimeMillis());
    }

    @Test
    void testProcessingLatencyCalculation() throws Exception {
        // 准备事件
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);

        long timestamp = System.currentTimeMillis() - 5000; // 5秒前
        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(afterData)
                .primaryKeys(List.of("id"))
                .eventId("test-event-6")
                .build();

        // 处理事件
        ProcessedEvent result = processor.map(changeEvent);

        // 验证延迟计算
        assertNotNull(result);
        long latency = result.getProcessingLatency();
        assertTrue(latency >= 5000, "Latency should be at least 5000ms");
        assertTrue(latency < 10000, "Latency should be less than 10000ms");
    }

    @Test
    void testNullChangeEvent() throws Exception {
        // 处理null事件
        ProcessedEvent result = processor.map(null);

        // 验证返回null
        assertNull(result);
    }

    @Test
    void testEventIdUniqueness() throws Exception {
        // 准备多个相同的事件（使用空字符串eventId）
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);

        long timestamp = System.currentTimeMillis();

        // 处理多个事件，使用空字符串eventId以触发生成
        ProcessedEvent result1 = processor.map(new ChangeEvent(
                "INSERT",
                "testdb",
                "users",
                timestamp,
                null,
                afterData,
                List.of("id"),
                ""  // 空eventId
        ));

        ProcessedEvent result2 = processor.map(new ChangeEvent(
                "INSERT",
                "testdb",
                "users",
                timestamp,
                null,
                afterData,
                List.of("id"),
                ""  // 空eventId
        ));

        // 验证生成的eventId不同
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotEquals(result1.getEventId(), result2.getEventId());
    }

    @Test
    void testMultipleEventTypes() throws Exception {
        // 测试处理不同类型的事件
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);

        long timestamp = System.currentTimeMillis();

        // INSERT
        ProcessedEvent insertResult = processor.map(ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("insert-1")
                .build());

        // UPDATE
        ProcessedEvent updateResult = processor.map(ChangeEvent.builder()
                .eventType("UPDATE")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .before(data)
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("update-1")
                .build());

        // DELETE
        ProcessedEvent deleteResult = processor.map(ChangeEvent.builder()
                .eventType("DELETE")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .before(data)
                .primaryKeys(List.of("id"))
                .eventId("delete-1")
                .build());

        // 验证所有事件都被正确处理
        assertNotNull(insertResult);
        assertNotNull(updateResult);
        assertNotNull(deleteResult);
        assertEquals("INSERT", insertResult.getEventType());
        assertEquals("UPDATE", updateResult.getEventType());
        assertEquals("DELETE", deleteResult.getEventType());
    }
}
