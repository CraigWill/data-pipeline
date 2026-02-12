package com.realtime.pipeline.consistency;

import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConsistencyValidator单元测试
 * 
 * 验证需求: 9.5
 */
class ConsistencyValidatorTest {

    private ConsistencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConsistencyValidator();
    }

    // ========== ChangeEvent验证测试 ==========

    @Test
    void testValidInsertEvent() {
        Map<String, Object> after = new HashMap<>();
        after.put("id", 1);
        after.put("name", "Alice");

        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(after)
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidUpdateEvent() {
        Map<String, Object> before = new HashMap<>();
        before.put("id", 1);
        before.put("name", "Alice");

        Map<String, Object> after = new HashMap<>();
        after.put("id", 1);
        after.put("name", "Bob");

        ChangeEvent event = ChangeEvent.builder()
                .eventType("UPDATE")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .before(before)
                .after(after)
                .primaryKeys(List.of("id"))
                .eventId("evt-002")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidDeleteEvent() {
        Map<String, Object> before = new HashMap<>();
        before.put("id", 1);
        before.put("name", "Alice");

        ChangeEvent event = ChangeEvent.builder()
                .eventType("DELETE")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .before(before)
                .primaryKeys(List.of("id"))
                .eventId("evt-003")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testNullEvent() {
        ValidationResult result = validator.validateChangeEvent(null);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Event is null"));
    }

    @Test
    void testMissingEventType() {
        ChangeEvent event = ChangeEvent.builder()
                .eventType(null)
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("id", 1))
                .primaryKeys(List.of("id"))
                .eventId("evt-004")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Event type is null")));
    }

    @Test
    void testInvalidEventType() {
        ChangeEvent event = ChangeEvent.builder()
                .eventType("INVALID")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("id", 1))
                .primaryKeys(List.of("id"))
                .eventId("evt-005")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Invalid event type")));
    }

    @Test
    void testInsertEventWithoutAfterData() {
        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(null)
                .primaryKeys(List.of("id"))
                .eventId("evt-006")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("INSERT event must have 'after' data")));
    }

    @Test
    void testUpdateEventWithoutBeforeData() {
        ChangeEvent event = ChangeEvent.builder()
                .eventType("UPDATE")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .before(null)
                .after(Map.of("id", 1, "name", "Bob"))
                .primaryKeys(List.of("id"))
                .eventId("evt-007")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("UPDATE event must have 'before' data")));
    }

    @Test
    void testDeleteEventWithoutBeforeData() {
        ChangeEvent event = ChangeEvent.builder()
                .eventType("DELETE")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .before(null)
                .primaryKeys(List.of("id"))
                .eventId("evt-008")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("DELETE event must have 'before' data")));
    }

    @Test
    void testMissingPrimaryKeys() {
        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("id", 1))
                .primaryKeys(null)
                .eventId("evt-009")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Primary keys list is null")));
    }

    @Test
    void testPrimaryKeyNotInData() {
        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("name", "Alice"))
                .primaryKeys(List.of("id"))
                .eventId("evt-010")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Primary key 'id' not found in data")));
    }

    @Test
    void testPrimaryKeyWithNullValue() {
        Map<String, Object> after = new HashMap<>();
        after.put("id", null);
        after.put("name", "Alice");

        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(after)
                .primaryKeys(List.of("id"))
                .eventId("evt-011")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Primary key 'id' has null value")));
    }

    @Test
    void testInvalidTimestamp() {
        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(0)
                .after(Map.of("id", 1))
                .primaryKeys(List.of("id"))
                .eventId("evt-012")
                .build();

        ValidationResult result = validator.validateChangeEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Timestamp is invalid")));
    }

    // ========== ProcessedEvent验证测试 ==========

    @Test
    void testValidProcessedEvent() {
        long timestamp = System.currentTimeMillis();
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(Map.of("id", 1, "name", "Alice"))
                .partition("users")
                .eventId("evt-001")
                .build();

        ValidationResult result = validator.validateProcessedEvent(event);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testProcessedEventWithInvalidProcessTime() {
        long timestamp = System.currentTimeMillis();
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp - 1000) // Process time before event timestamp
                .data(Map.of("id", 1, "name", "Alice"))
                .partition("users")
                .eventId("evt-001")
                .build();

        ValidationResult result = validator.validateProcessedEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Process time") && e.contains("before event timestamp")));
    }

    @Test
    void testProcessedEventWithoutData() {
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .processTime(System.currentTimeMillis())
                .data(null)
                .partition("users")
                .eventId("evt-001")
                .build();

        ValidationResult result = validator.validateProcessedEvent(event);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Data is null")));
    }

    // ========== 事件一致性验证测试 ==========

    @Test
    void testConsistentEvents() {
        long timestamp = System.currentTimeMillis();
        Map<String, Object> data = Map.of("id", 1, "name", "Alice");

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(data)
                .partition("users")
                .eventId("evt-001")
                .build();

        ValidationResult result = validator.validateEventConsistency(changeEvent, processedEvent);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testEventIdMismatch() {
        long timestamp = System.currentTimeMillis();

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(Map.of("id", 1))
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(Map.of("id", 1))
                .partition("users")
                .eventId("evt-002")
                .build();

        ValidationResult result = validator.validateEventConsistency(changeEvent, processedEvent);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Event ID mismatch")));
    }

    @Test
    void testEventTypeMismatch() {
        long timestamp = System.currentTimeMillis();

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(Map.of("id", 1))
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventType("UPDATE")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(Map.of("id", 1))
                .partition("users")
                .eventId("evt-001")
                .build();

        ValidationResult result = validator.validateEventConsistency(changeEvent, processedEvent);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Event type mismatch")));
    }

    @Test
    void testPrimaryKeyValueMismatch() {
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

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(Map.of("id", 2, "name", "Alice"))
                .partition("users")
                .eventId("evt-001")
                .build();

        ValidationResult result = validator.validateEventConsistency(changeEvent, processedEvent);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Primary key 'id' value mismatch")));
    }

    @Test
    void testTimestampMismatch() {
        long timestamp1 = System.currentTimeMillis();
        long timestamp2 = timestamp1 + 5000;

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp1)
                .after(Map.of("id", 1))
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp2)
                .processTime(timestamp2 + 1000)
                .data(Map.of("id", 1))
                .partition("users")
                .eventId("evt-001")
                .build();

        ValidationResult result = validator.validateEventConsistency(changeEvent, processedEvent);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Timestamp mismatch")));
    }
}
