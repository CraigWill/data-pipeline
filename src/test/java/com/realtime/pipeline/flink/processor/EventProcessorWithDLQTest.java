package com.realtime.pipeline.flink.processor;

import com.realtime.pipeline.error.DeadLetterQueue;
import com.realtime.pipeline.error.FileBasedDeadLetterQueue;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.configuration.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EventProcessorWithDLQ的单元测试
 * 
 * 测试场景:
 * - 正常处理事件
 * - 处理异常时发送到死信队列
 * - 空事件处理
 * - 事件ID生成
 */
class EventProcessorWithDLQTest {

    @TempDir
    Path tempDir;

    private EventProcessorWithDLQ processor;
    private DeadLetterQueue deadLetterQueue;
    private String dlqPath;

    @BeforeEach
    void setUp() throws Exception {
        dlqPath = tempDir.resolve("dlq").toString();
        processor = new EventProcessorWithDLQ(dlqPath);
        processor.open(new Configuration());
        deadLetterQueue = new FileBasedDeadLetterQueue(dlqPath);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (processor != null) {
            processor.close();
        }
        if (deadLetterQueue != null) {
            deadLetterQueue.close();
        }
    }

    @Test
    void testSuccessfulProcessing() throws Exception {
        // 准备测试数据
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "test");

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(data)
                .eventId("test-event-1")
                .build();

        // 执行处理
        ProcessedEvent result = processor.map(changeEvent);

        // 验证结果
        assertNotNull(result);
        assertEquals("INSERT", result.getEventType());
        assertEquals("testdb", result.getDatabase());
        assertEquals("users", result.getTable());
        assertEquals("test-event-1", result.getEventId());
        assertNotNull(result.getPartition());
        assertEquals(data, result.getData());

        // 验证没有记录进入死信队列
        assertEquals(0, deadLetterQueue.count());
    }

    @Test
    void testProcessingWithGeneratedEventId() throws Exception {
        // 准备测试数据（没有eventId）
        // 注意: ChangeEvent构造函数会自动生成UUID如果eventId为null
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(data)
                .eventId(null)  // 没有eventId
                .build();

        // 验证ChangeEvent已经有了自动生成的eventId
        assertNotNull(changeEvent.getEventId());

        // 执行处理
        ProcessedEvent result = processor.map(changeEvent);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getEventId());
        // 应该使用ChangeEvent中已经生成的eventId
        assertEquals(changeEvent.getEventId(), result.getEventId());
    }

    @Test
    void testNullEventHandling() throws Exception {
        // 处理null事件
        ProcessedEvent result = processor.map(null);

        // 验证返回null
        assertNull(result);

        // 验证没有记录进入死信队列
        assertEquals(0, deadLetterQueue.count());
    }

    @Test
    void testProcessingExceptionSendsToDeadLetterQueue() throws Exception {
        // 创建一个会导致处理失败的processor
        // 我们通过创建一个特殊的ChangeEvent来触发异常
        EventProcessorWithDLQ faultyProcessor = new EventProcessorWithDLQ(dlqPath) {
            @Override
            protected String generatePartition(long timestamp) {
                // 对特定的eventId抛出异常
                throw new RuntimeException("Simulated processing failure");
            }
        };
        faultyProcessor.open(new Configuration());

        try {
            // 准备测试数据
            Map<String, Object> data = new HashMap<>();
            data.put("id", 1);

            ChangeEvent changeEvent = ChangeEvent.builder()
                    .eventType("INSERT")
                    .database("testdb")
                    .table("users")
                    .timestamp(System.currentTimeMillis())
                    .after(data)
                    .eventId("fail-event-1")
                    .build();

            // 执行处理（应该捕获异常并返回null）
            ProcessedEvent result = faultyProcessor.map(changeEvent);

            // 验证返回null（因为处理失败）
            assertNull(result);

            // 验证记录进入了死信队列
            assertEquals(1, deadLetterQueue.count());

            // 验证死信记录的内容
            List<DeadLetterRecord> dlqRecords = deadLetterQueue.listAll();
            assertEquals(1, dlqRecords.size());

            DeadLetterRecord dlqRecord = dlqRecords.get(0);
            assertEquals("fail-event-1", dlqRecord.getEventId());
            assertEquals("EventProcessor", dlqRecord.getComponent());
            assertEquals("PROCESS", dlqRecord.getOperationType());
            assertEquals("CHANGE_EVENT", dlqRecord.getDataType());
            assertNotNull(dlqRecord.getFailureReason());
            assertTrue(dlqRecord.getFailureReason().contains("Simulated processing failure"));
            assertNotNull(dlqRecord.getStackTrace());
            assertFalse(dlqRecord.isReprocessed());

            // 验证上下文信息
            Map<String, String> context = dlqRecord.getContext();
            assertNotNull(context);
            assertEquals("testdb", context.get("database"));
            assertEquals("users", context.get("table"));
            assertEquals("INSERT", context.get("eventType"));

        } finally {
            faultyProcessor.close();
        }
    }

    @Test
    void testPartitionGeneration() throws Exception {
        // 准备测试数据
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);

        // 使用特定的时间戳: 2025-01-28 12:30:00
        long timestamp = 1738069800000L;

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(data)
                .eventId("test-event-1")
                .build();

        // 执行处理
        ProcessedEvent result = processor.map(changeEvent);

        // 验证分区格式（yyyyMMddHH）
        assertNotNull(result);
        assertNotNull(result.getPartition());
        assertEquals(10, result.getPartition().length());
        assertTrue(result.getPartition().matches("\\d{10}"));
    }

    @Test
    void testProcessingLatency() throws Exception {
        // 准备测试数据
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);

        long timestamp = System.currentTimeMillis() - 1000; // 1秒前

        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(timestamp)
                .after(data)
                .eventId("test-event-1")
                .build();

        // 执行处理
        ProcessedEvent result = processor.map(changeEvent);

        // 验证处理延迟
        assertNotNull(result);
        assertTrue(result.getProcessingLatency() >= 1000);
        assertTrue(result.getProcessTime() > timestamp);
    }

    @Test
    void testMultipleEventsProcessing() throws Exception {
        // 处理多个事件
        for (int i = 0; i < 5; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", i);

            ChangeEvent changeEvent = ChangeEvent.builder()
                    .eventType("INSERT")
                    .database("testdb")
                    .table("users")
                    .timestamp(System.currentTimeMillis())
                    .after(data)
                    .eventId("test-event-" + i)
                    .build();

            ProcessedEvent result = processor.map(changeEvent);
            assertNotNull(result);
            assertEquals("test-event-" + i, result.getEventId());
        }

        // 验证没有记录进入死信队列
        assertEquals(0, deadLetterQueue.count());
    }
}
