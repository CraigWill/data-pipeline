package com.realtime.pipeline.flink.processor;

import com.realtime.pipeline.model.ProcessedEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProcessedEventValidator单元测试
 * 
 * 验证需求: 9.5
 */
class ProcessedEventValidatorTest {

    @Test
    void testMapValidEvent() throws Exception {
        ProcessedEventValidator validator = new ProcessedEventValidator();

        long timestamp = System.currentTimeMillis();
        ProcessedEvent validEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(Map.of("id", 1, "name", "Alice"))
                .partition("users")
                .eventId("evt-001")
                .build();

        ProcessedEvent result = validator.map(validEvent);
        assertNotNull(result);
        assertEquals(validEvent, result, "Valid event should be returned unchanged");
    }

    @Test
    void testMapInvalidEvent() throws Exception {
        ProcessedEventValidator validator = new ProcessedEventValidator();

        long timestamp = System.currentTimeMillis();
        ProcessedEvent invalidEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp - 1000) // Invalid: process time before event timestamp
                .data(Map.of("id", 1, "name", "Alice"))
                .partition("users")
                .eventId("evt-002")
                .build();

        ProcessedEvent result = validator.map(invalidEvent);
        assertNotNull(result);
        assertEquals(invalidEvent, result, "Invalid event should still be returned (but logged)");
    }

    @Test
    void testMapEventWithoutData() throws Exception {
        ProcessedEventValidator validator = new ProcessedEventValidator();

        long timestamp = System.currentTimeMillis();
        ProcessedEvent invalidEvent = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(null) // Invalid: data is null
                .partition("users")
                .eventId("evt-003")
                .build();

        ProcessedEvent result = validator.map(invalidEvent);
        assertNotNull(result);
        assertEquals(invalidEvent, result, "Event without data should still be returned (but logged)");
    }

    @Test
    void testMapEventWithInvalidEventType() throws Exception {
        ProcessedEventValidator validator = new ProcessedEventValidator();

        long timestamp = System.currentTimeMillis();
        ProcessedEvent invalidEvent = ProcessedEvent.builder()
                .eventType("INVALID_TYPE")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .processTime(timestamp + 1000)
                .data(Map.of("id", 1, "name", "Alice"))
                .partition("users")
                .eventId("evt-004")
                .build();

        ProcessedEvent result = validator.map(invalidEvent);
        assertNotNull(result);
        assertEquals(invalidEvent, result, "Event with invalid type should still be returned (but logged)");
    }
}
