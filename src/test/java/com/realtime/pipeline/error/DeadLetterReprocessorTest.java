package com.realtime.pipeline.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 死信队列重新处理器单元测试
 */
class DeadLetterReprocessorTest {

    @TempDir
    Path tempDir;

    private FileBasedDeadLetterQueue deadLetterQueue;
    private DeadLetterReprocessor reprocessor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        deadLetterQueue = new FileBasedDeadLetterQueue(tempDir.toString());
        reprocessor = new DeadLetterReprocessor(deadLetterQueue);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (deadLetterQueue != null) {
            deadLetterQueue.close();
        }
    }

    @Test
    void testReprocessChangeEventRecord() throws IOException {
        // 创建ChangeEvent
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

        // 创建死信记录
        DeadLetterRecord record = DeadLetterRecord.builder()
                .recordId("record-1")
                .eventId("evt-12345")
                .failureTimestamp(System.currentTimeMillis())
                .failureReason("Processing failed")
                .component("EventProcessor")
                .operationType("PROCESS")
                .retryCount(3)
                .originalData(objectMapper.writeValueAsString(event))
                .dataType("CHANGE_EVENT")
                .context(new HashMap<>())
                .reprocessed(false)
                .reprocessTimestamp(null)
                .build();

        deadLetterQueue.add(record);

        // 重新处理
        AtomicInteger processedCount = new AtomicInteger(0);
        boolean success = reprocessor.reprocessRecord(
                "record-1",
                changeEvent -> {
                    assertEquals("evt-12345", changeEvent.getEventId());
                    assertEquals("INSERT", changeEvent.getEventType());
                    assertEquals("testdb", changeEvent.getDatabase());
                    processedCount.incrementAndGet();
                },
                processedEvent -> fail("Should not process ProcessedEvent")
        );

        assertTrue(success);
        assertEquals(1, processedCount.get());

        // 验证记录已标记为已处理
        Optional<DeadLetterRecord> updated = deadLetterQueue.get("record-1");
        assertTrue(updated.isPresent());
        assertTrue(updated.get().isReprocessed());
        assertNotNull(updated.get().getReprocessTimestamp());
    }

    @Test
    void testReprocessProcessedEventRecord() throws IOException {
        // 创建ProcessedEvent
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

        // 创建死信记录
        DeadLetterRecord record = DeadLetterRecord.builder()
                .recordId("record-1")
                .eventId("evt-12345")
                .failureTimestamp(System.currentTimeMillis())
                .failureReason("Write failed")
                .component("FileSink")
                .operationType("WRITE")
                .retryCount(3)
                .originalData(objectMapper.writeValueAsString(event))
                .dataType("PROCESSED_EVENT")
                .context(new HashMap<>())
                .reprocessed(false)
                .reprocessTimestamp(null)
                .build();

        deadLetterQueue.add(record);

        // 重新处理
        AtomicInteger processedCount = new AtomicInteger(0);
        boolean success = reprocessor.reprocessRecord(
                "record-1",
                changeEvent -> fail("Should not process ChangeEvent"),
                processedEvent -> {
                    assertEquals("evt-12345", processedEvent.getEventId());
                    assertEquals("INSERT", processedEvent.getEventType());
                    assertEquals("testdb", processedEvent.getDatabase());
                    processedCount.incrementAndGet();
                }
        );

        assertTrue(success);
        assertEquals(1, processedCount.get());

        // 验证记录已标记为已处理
        Optional<DeadLetterRecord> updated = deadLetterQueue.get("record-1");
        assertTrue(updated.isPresent());
        assertTrue(updated.get().isReprocessed());
    }

    @Test
    void testReprocessNonExistentRecord() {
        boolean success = reprocessor.reprocessRecord(
                "non-existent",
                changeEvent -> fail("Should not be called"),
                processedEvent -> fail("Should not be called")
        );

        assertFalse(success);
    }

    @Test
    void testReprocessAlreadyProcessedRecord() throws IOException {
        // 创建已处理的记录
        ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(new HashMap<>())
                .primaryKeys(List.of("id"))
                .eventId("evt-12345")
                .build();

        DeadLetterRecord record = DeadLetterRecord.builder()
                .recordId("record-1")
                .eventId("evt-12345")
                .failureTimestamp(System.currentTimeMillis())
                .failureReason("Processing failed")
                .component("EventProcessor")
                .operationType("PROCESS")
                .retryCount(3)
                .originalData(objectMapper.writeValueAsString(event))
                .dataType("CHANGE_EVENT")
                .context(new HashMap<>())
                .reprocessed(true)
                .reprocessTimestamp(System.currentTimeMillis())
                .build();

        deadLetterQueue.add(record);

        // 尝试重新处理
        boolean success = reprocessor.reprocessRecord(
                "record-1",
                changeEvent -> fail("Should not be called"),
                processedEvent -> fail("Should not be called")
        );

        assertFalse(success);
    }

    @Test
    void testReprocessAll() throws IOException {
        // 添加多条未处理的记录
        for (int i = 1; i <= 5; i++) {
            ChangeEvent event = ChangeEvent.builder()
                    .eventType("INSERT")
                    .database("testdb")
                    .table("users")
                    .timestamp(System.currentTimeMillis())
                    .after(new HashMap<>())
                    .primaryKeys(List.of("id"))
                    .eventId("evt-" + i)
                    .build();

            DeadLetterRecord record = DeadLetterRecord.builder()
                    .recordId("record-" + i)
                    .eventId("evt-" + i)
                    .failureTimestamp(System.currentTimeMillis())
                    .failureReason("Processing failed")
                    .component("EventProcessor")
                    .operationType("PROCESS")
                    .retryCount(3)
                    .originalData(objectMapper.writeValueAsString(event))
                    .dataType("CHANGE_EVENT")
                    .context(new HashMap<>())
                    .reprocessed(false)
                    .reprocessTimestamp(null)
                    .build();

            deadLetterQueue.add(record);
        }

        // 重新处理所有记录
        AtomicInteger processedCount = new AtomicInteger(0);
        DeadLetterReprocessor.ReprocessResult result = reprocessor.reprocessAll(
                changeEvent -> processedCount.incrementAndGet(),
                processedEvent -> fail("Should not process ProcessedEvent")
        );

        assertTrue(result.isSuccess());
        assertEquals(5, result.totalRecords);
        assertEquals(5, result.successCount);
        assertEquals(0, result.failureCount);
        assertEquals(5, processedCount.get());

        // 验证所有记录都已标记为已处理
        List<DeadLetterRecord> unprocessed = deadLetterQueue.listUnprocessed();
        assertEquals(0, unprocessed.size());
    }

    @Test
    void testReprocessAllWithMixedTypes() throws IOException {
        // 添加ChangeEvent记录
        for (int i = 1; i <= 3; i++) {
            ChangeEvent event = ChangeEvent.builder()
                    .eventType("INSERT")
                    .database("testdb")
                    .table("users")
                    .timestamp(System.currentTimeMillis())
                    .after(new HashMap<>())
                    .primaryKeys(List.of("id"))
                    .eventId("evt-" + i)
                    .build();

            DeadLetterRecord record = DeadLetterRecord.builder()
                    .recordId("change-record-" + i)
                    .eventId("evt-" + i)
                    .failureTimestamp(System.currentTimeMillis())
                    .failureReason("Processing failed")
                    .component("EventProcessor")
                    .operationType("PROCESS")
                    .retryCount(3)
                    .originalData(objectMapper.writeValueAsString(event))
                    .dataType("CHANGE_EVENT")
                    .context(new HashMap<>())
                    .reprocessed(false)
                    .reprocessTimestamp(null)
                    .build();

            deadLetterQueue.add(record);
        }

        // 添加ProcessedEvent记录
        for (int i = 1; i <= 2; i++) {
            ProcessedEvent event = ProcessedEvent.builder()
                    .eventType("INSERT")
                    .database("testdb")
                    .table("users")
                    .timestamp(System.currentTimeMillis())
                    .processTime(System.currentTimeMillis())
                    .data(new HashMap<>())
                    .partition("2025-01-28")
                    .eventId("evt-processed-" + i)
                    .build();

            DeadLetterRecord record = DeadLetterRecord.builder()
                    .recordId("processed-record-" + i)
                    .eventId("evt-processed-" + i)
                    .failureTimestamp(System.currentTimeMillis())
                    .failureReason("Write failed")
                    .component("FileSink")
                    .operationType("WRITE")
                    .retryCount(3)
                    .originalData(objectMapper.writeValueAsString(event))
                    .dataType("PROCESSED_EVENT")
                    .context(new HashMap<>())
                    .reprocessed(false)
                    .reprocessTimestamp(null)
                    .build();

            deadLetterQueue.add(record);
        }

        // 重新处理所有记录
        AtomicInteger changeEventCount = new AtomicInteger(0);
        AtomicInteger processedEventCount = new AtomicInteger(0);

        DeadLetterReprocessor.ReprocessResult result = reprocessor.reprocessAll(
                changeEvent -> changeEventCount.incrementAndGet(),
                processedEvent -> processedEventCount.incrementAndGet()
        );

        assertTrue(result.isSuccess());
        assertEquals(5, result.totalRecords);
        assertEquals(5, result.successCount);
        assertEquals(0, result.failureCount);
        assertEquals(3, changeEventCount.get());
        assertEquals(2, processedEventCount.get());
    }

    @Test
    void testReprocessByComponent() throws IOException {
        // 添加不同组件的记录
        String[] components = {"EventProcessor", "FileSink", "EventProcessor", "DataHubSender", "EventProcessor"};

        for (int i = 0; i < components.length; i++) {
            ChangeEvent event = ChangeEvent.builder()
                    .eventType("INSERT")
                    .database("testdb")
                    .table("users")
                    .timestamp(System.currentTimeMillis())
                    .after(new HashMap<>())
                    .primaryKeys(List.of("id"))
                    .eventId("evt-" + i)
                    .build();

            DeadLetterRecord record = DeadLetterRecord.builder()
                    .recordId("record-" + i)
                    .eventId("evt-" + i)
                    .failureTimestamp(System.currentTimeMillis())
                    .failureReason("Processing failed")
                    .component(components[i])
                    .operationType("PROCESS")
                    .retryCount(3)
                    .originalData(objectMapper.writeValueAsString(event))
                    .dataType("CHANGE_EVENT")
                    .context(new HashMap<>())
                    .reprocessed(false)
                    .reprocessTimestamp(null)
                    .build();

            deadLetterQueue.add(record);
        }

        // 只重新处理EventProcessor的记录
        AtomicInteger processedCount = new AtomicInteger(0);
        DeadLetterReprocessor.ReprocessResult result = reprocessor.reprocessByComponent(
                "EventProcessor",
                changeEvent -> processedCount.incrementAndGet(),
                processedEvent -> fail("Should not process ProcessedEvent")
        );

        assertTrue(result.isSuccess());
        assertEquals(3, result.totalRecords);
        assertEquals(3, result.successCount);
        assertEquals(0, result.failureCount);
        assertEquals(3, processedCount.get());

        // 验证其他组件的记录仍未处理
        List<DeadLetterRecord> unprocessed = deadLetterQueue.listUnprocessed();
        assertEquals(2, unprocessed.size());
    }

    @Test
    void testReprocessResultToString() {
        DeadLetterReprocessor.ReprocessResult result = new DeadLetterReprocessor.ReprocessResult();
        result.totalRecords = 10;
        result.successCount = 8;
        result.failureCount = 2;
        result.failedRecordIds.add("record-1");
        result.failedRecordIds.add("record-2");

        String str = result.toString();
        assertTrue(str.contains("total=10"));
        assertTrue(str.contains("success=8"));
        assertTrue(str.contains("failure=2"));
    }

    @Test
    void testReprocessResultIsSuccess() {
        DeadLetterReprocessor.ReprocessResult result1 = new DeadLetterReprocessor.ReprocessResult();
        result1.totalRecords = 5;
        result1.successCount = 5;
        result1.failureCount = 0;
        result1.error = null;
        assertTrue(result1.isSuccess());

        DeadLetterReprocessor.ReprocessResult result2 = new DeadLetterReprocessor.ReprocessResult();
        result2.totalRecords = 5;
        result2.successCount = 4;
        result2.failureCount = 1;
        result2.error = null;
        assertFalse(result2.isSuccess());

        DeadLetterReprocessor.ReprocessResult result3 = new DeadLetterReprocessor.ReprocessResult();
        result3.totalRecords = 5;
        result3.successCount = 5;
        result3.failureCount = 0;
        result3.error = "Some error";
        assertFalse(result3.isSuccess());
    }
}
