package com.realtime.pipeline.flink.processor;

import com.realtime.pipeline.model.ChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConsistencyCheckFunction单元测试
 * 
 * 验证需求: 9.5
 */
class ConsistencyCheckFunctionTest {

    @Test
    void testFilterValidEvent() throws Exception {
        ConsistencyCheckFunction function = new ConsistencyCheckFunction(false);

        ChangeEvent validEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("id", 1, "name", "Alice"))
                .primaryKeys(List.of("id"))
                .eventId("evt-001")
                .build();

        boolean result = function.filter(validEvent);
        assertTrue(result, "Valid event should pass the filter");
    }

    @Test
    void testFilterInvalidEventWithoutFiltering() throws Exception {
        ConsistencyCheckFunction function = new ConsistencyCheckFunction(false);

        ChangeEvent invalidEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(null) // Invalid: INSERT must have 'after' data
                .primaryKeys(List.of("id"))
                .eventId("evt-002")
                .build();

        boolean result = function.filter(invalidEvent);
        assertTrue(result, "Invalid event should pass when filterInvalid=false");
    }

    @Test
    void testFilterInvalidEventWithFiltering() throws Exception {
        ConsistencyCheckFunction function = new ConsistencyCheckFunction(true);

        ChangeEvent invalidEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(null) // Invalid: INSERT must have 'after' data
                .primaryKeys(List.of("id"))
                .eventId("evt-003")
                .build();

        boolean result = function.filter(invalidEvent);
        assertFalse(result, "Invalid event should be filtered when filterInvalid=true");
    }

    @Test
    void testFilterEventWithMissingPrimaryKey() throws Exception {
        ConsistencyCheckFunction function = new ConsistencyCheckFunction(true);

        ChangeEvent invalidEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("name", "Alice")) // Missing primary key 'id'
                .primaryKeys(List.of("id"))
                .eventId("evt-004")
                .build();

        boolean result = function.filter(invalidEvent);
        assertFalse(result, "Event with missing primary key should be filtered");
    }

    @Test
    void testFilterEventWithInvalidEventType() throws Exception {
        ConsistencyCheckFunction function = new ConsistencyCheckFunction(true);

        ChangeEvent invalidEvent = ChangeEvent.builder()
                .eventType("INVALID_TYPE")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(Map.of("id", 1, "name", "Alice"))
                .primaryKeys(List.of("id"))
                .eventId("evt-005")
                .build();

        boolean result = function.filter(invalidEvent);
        assertFalse(result, "Event with invalid event type should be filtered");
    }

    @Test
    void testDefaultConstructor() throws Exception {
        ConsistencyCheckFunction function = new ConsistencyCheckFunction();

        ChangeEvent invalidEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(null)
                .primaryKeys(List.of("id"))
                .eventId("evt-006")
                .build();

        boolean result = function.filter(invalidEvent);
        assertTrue(result, "Default constructor should not filter invalid events");
    }
}
