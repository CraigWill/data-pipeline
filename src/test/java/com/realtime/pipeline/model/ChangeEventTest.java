package com.realtime.pipeline.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChangeEvent模型测试
 */
class ChangeEventTest {

    @Test
    void testCreateInsertEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "test");

        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("test_db")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(data)
                .primaryKeys(List.of("id"))
                .build();

        assertTrue(event.isInsert());
        assertFalse(event.isUpdate());
        assertFalse(event.isDelete());
        assertEquals(data, event.getData());
        assertNotNull(event.getEventId());
    }

    @Test
    void testCreateUpdateEvent() {
        Map<String, Object> before = new HashMap<>();
        before.put("id", 1);
        before.put("name", "old");

        Map<String, Object> after = new HashMap<>();
        after.put("id", 1);
        after.put("name", "new");

        ChangeEvent event = ChangeEvent.builder()
                .eventType("UPDATE")
                .database("test_db")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .before(before)
                .after(after)
                .primaryKeys(List.of("id"))
                .build();

        assertFalse(event.isInsert());
        assertTrue(event.isUpdate());
        assertFalse(event.isDelete());
        assertEquals(after, event.getData());
        assertNotNull(event.getBefore());
    }

    @Test
    void testCreateDeleteEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "test");

        ChangeEvent event = ChangeEvent.builder()
                .eventType("DELETE")
                .database("test_db")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .before(data)
                .primaryKeys(List.of("id"))
                .build();

        assertFalse(event.isInsert());
        assertFalse(event.isUpdate());
        assertTrue(event.isDelete());
        assertEquals(data, event.getData());
    }
}
