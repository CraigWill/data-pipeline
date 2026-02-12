package com.realtime.pipeline.error;

import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 死信记录构建器单元测试
 */
class DeadLetterRecordBuilderTest {

    @Test
    void testBuildBasicRecord() {
        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .retryCount(3)
                .originalData("{\"test\":\"data\"}")
                .dataType("CHANGE_EVENT")
                .build();

        assertNotNull(record);
        assertNotNull(record.getRecordId());
        assertEquals("evt-12345", record.getEventId());
        assertEquals("Test failure", record.getFailureReason());
        assertEquals("TestComponent", record.getComponent());
        assertEquals("TEST", record.getOperationType());
        assertEquals(3, record.getRetryCount());
        assertEquals("{\"test\":\"data\"}", record.getOriginalData());
        assertEquals("CHANGE_EVENT", record.getDataType());
        assertFalse(record.isReprocessed());
        assertNull(record.getReprocessTimestamp());
    }

    @Test
    void testBuildFromChangeEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "Test");

        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("evt-12345")
                .build();

        DeadLetterRecord record = DeadLetterRecordBuilder.fromChangeEvent(event)
                .component("EventProcessor")
                .operationType("PROCESS")
                .failureReason("Processing failed")
                .retryCount(2)
                .build();

        assertNotNull(record);
        assertEquals("evt-12345", record.getEventId());
        assertEquals("CHANGE_EVENT", record.getDataType());
        assertNotNull(record.getOriginalData());
        assertTrue(record.getOriginalData().contains("INSERT"));
        assertTrue(record.getOriginalData().contains("testdb"));
        assertEquals("EventProcessor", record.getComponent());
        assertEquals("PROCESS", record.getOperationType());
        assertEquals(2, record.getRetryCount());
    }

    @Test
    void testBuildFromProcessedEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "Test");

        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .processTime(System.currentTimeMillis())
                .data(data)
                .partition("2025-01-28")
                .eventId("evt-12345")
                .build();

        DeadLetterRecord record = DeadLetterRecordBuilder.fromProcessedEvent(event)
                .component("FileSink")
                .operationType("WRITE")
                .failureReason("Write failed")
                .retryCount(3)
                .build();

        assertNotNull(record);
        assertEquals("evt-12345", record.getEventId());
        assertEquals("PROCESSED_EVENT", record.getDataType());
        assertNotNull(record.getOriginalData());
        assertTrue(record.getOriginalData().contains("INSERT"));
        assertTrue(record.getOriginalData().contains("testdb"));
        assertEquals("FileSink", record.getComponent());
        assertEquals("WRITE", record.getOperationType());
        assertEquals(3, record.getRetryCount());
    }

    @Test
    void testBuildWithException() {
        Exception exception = new RuntimeException("Test exception message");

        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .exception(exception)
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .build();

        assertNotNull(record);
        assertEquals("Test exception message", record.getFailureReason());
        assertNotNull(record.getStackTrace());
        assertTrue(record.getStackTrace().contains("RuntimeException"));
        assertTrue(record.getStackTrace().contains("Test exception message"));
    }

    @Test
    void testBuildWithNestedExceptions() {
        Exception rootCause = new IllegalArgumentException("Root cause");
        Exception wrappedException = new RuntimeException("Wrapped exception", rootCause);

        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .exception(wrappedException)
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .build();

        assertNotNull(record);
        assertEquals("Wrapped exception", record.getFailureReason());
        assertNotNull(record.getStackTrace());
        assertTrue(record.getStackTrace().contains("RuntimeException"));
        assertTrue(record.getStackTrace().contains("IllegalArgumentException"));
        assertTrue(record.getStackTrace().contains("Root cause"));
    }

    @Test
    void testBuildWithContext() {
        Map<String, String> context = new HashMap<>();
        context.put("outputPath", "/data/output");
        context.put("fileSize", "1024000");
        context.put("retryAttempt", "3");

        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .context(context)
                .build();

        assertNotNull(record);
        assertNotNull(record.getContext());
        assertEquals(3, record.getContext().size());
        assertEquals("/data/output", record.getContext().get("outputPath"));
        assertEquals("1024000", record.getContext().get("fileSize"));
        assertEquals("3", record.getContext().get("retryAttempt"));
    }

    @Test
    void testAddContextIncrementally() {
        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .addContext("key1", "value1")
                .addContext("key2", "value2")
                .addContext("key3", "value3")
                .build();

        assertNotNull(record);
        assertNotNull(record.getContext());
        assertEquals(3, record.getContext().size());
        assertEquals("value1", record.getContext().get("key1"));
        assertEquals("value2", record.getContext().get("key2"));
        assertEquals("value3", record.getContext().get("key3"));
    }

    @Test
    void testCustomRecordId() {
        String customId = "custom-record-id-123";

        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .recordId(customId)
                .eventId("evt-12345")
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .build();

        assertNotNull(record);
        assertEquals(customId, record.getRecordId());
    }

    @Test
    void testCustomFailureTimestamp() {
        long customTimestamp = System.currentTimeMillis() - 10000;

        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .failureTimestamp(customTimestamp)
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .build();

        assertNotNull(record);
        assertEquals(customTimestamp, record.getFailureTimestamp());
    }

    @Test
    void testDefaultValues() {
        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .build();

        assertNotNull(record);
        assertNotNull(record.getRecordId()); // 自动生成
        assertTrue(record.getFailureTimestamp() > 0); // 自动设置为当前时间
        assertNotNull(record.getContext()); // 自动初始化为空Map
        assertEquals(0, record.getRetryCount()); // 默认为0
        assertFalse(record.isReprocessed()); // 默认为false
        assertNull(record.getReprocessTimestamp()); // 默认为null
    }

    @Test
    void testNullException() {
        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .exception(null)
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .build();

        assertNotNull(record);
        assertNull(record.getFailureReason());
        assertNull(record.getStackTrace());
    }

    @Test
    void testNullContext() {
        DeadLetterRecord record = DeadLetterRecordBuilder.builder()
                .eventId("evt-12345")
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .context(null)
                .build();

        assertNotNull(record);
        assertNotNull(record.getContext());
        assertTrue(record.getContext().isEmpty());
    }
}
