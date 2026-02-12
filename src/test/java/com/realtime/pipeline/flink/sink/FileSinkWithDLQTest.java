package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.error.DeadLetterQueue;
import com.realtime.pipeline.error.FileBasedDeadLetterQueue;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileSinkWithDLQ的单元测试
 * 
 * 测试场景:
 * - 正常写入
 * - 写入失败后重试
 * - 所有重试失败后发送到死信队列
 * - 空事件处理
 */
class FileSinkWithDLQTest {

    @TempDir
    Path tempDir;

    private DeadLetterQueue deadLetterQueue;
    private String dlqPath;

    @BeforeEach
    void setUp() throws Exception {
        dlqPath = tempDir.resolve("dlq").toString();
        deadLetterQueue = new FileBasedDeadLetterQueue(dlqPath);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (deadLetterQueue != null) {
            deadLetterQueue.close();
        }
    }

    @Test
    void testSuccessfulWrite() throws Exception {
        // 创建一个成功的mock sink
        AtomicInteger writeCount = new AtomicInteger(0);
        SinkFunction<ProcessedEvent> mockSink = new SinkFunction<ProcessedEvent>() {
            @Override
            public void invoke(ProcessedEvent value, Context context) throws Exception {
                writeCount.incrementAndGet();
            }
        };

        FileSinkWithDLQ sinkWithDLQ = new FileSinkWithDLQ(mockSink, dlqPath, 3);
        sinkWithDLQ.open(new Configuration());

        try {
            // 准备测试数据
            ProcessedEvent event = createTestEvent("test-event-1");

            // 执行写入
            sinkWithDLQ.invoke(event, null);

            // 验证写入成功
            assertEquals(1, writeCount.get());

            // 验证没有记录进入死信队列
            assertEquals(0, deadLetterQueue.count());

        } finally {
            sinkWithDLQ.close();
        }
    }

    @Test
    void testWriteWithRetrySuccess() throws Exception {
        // 创建一个前2次失败，第3次成功的mock sink
        AtomicInteger attemptCount = new AtomicInteger(0);
        SinkFunction<ProcessedEvent> mockSink = new SinkFunction<ProcessedEvent>() {
            @Override
            public void invoke(ProcessedEvent value, Context context) throws Exception {
                int attempt = attemptCount.incrementAndGet();
                if (attempt <= 2) {
                    throw new RuntimeException("Simulated write failure " + attempt);
                }
                // 第3次成功
            }
        };

        FileSinkWithDLQ sinkWithDLQ = new FileSinkWithDLQ(mockSink, dlqPath, 3);
        sinkWithDLQ.open(new Configuration());

        try {
            // 准备测试数据
            ProcessedEvent event = createTestEvent("test-event-1");

            // 执行写入（应该重试后成功）
            sinkWithDLQ.invoke(event, null);

            // 验证重试了3次
            assertEquals(3, attemptCount.get());

            // 验证没有记录进入死信队列（因为最终成功了）
            assertEquals(0, deadLetterQueue.count());

        } finally {
            sinkWithDLQ.close();
        }
    }

    @Test
    void testWriteFailureAfterAllRetriesSendsToDeadLetterQueue() throws Exception {
        // 创建一个总是失败的mock sink
        AtomicInteger attemptCount = new AtomicInteger(0);
        SinkFunction<ProcessedEvent> mockSink = new SinkFunction<ProcessedEvent>() {
            @Override
            public void invoke(ProcessedEvent value, Context context) throws Exception {
                attemptCount.incrementAndGet();
                throw new RuntimeException("Simulated persistent write failure");
            }
        };

        int maxRetries = 3;
        FileSinkWithDLQ sinkWithDLQ = new FileSinkWithDLQ(mockSink, dlqPath, maxRetries);
        sinkWithDLQ.open(new Configuration());

        try {
            // 准备测试数据
            ProcessedEvent event = createTestEvent("test-event-1");

            // 执行写入（应该失败并发送到死信队列）
            sinkWithDLQ.invoke(event, null);

            // 验证尝试了maxRetries+1次（初始尝试 + 3次重试）
            assertEquals(maxRetries + 1, attemptCount.get());

            // 验证记录进入了死信队列
            assertEquals(1, deadLetterQueue.count());

            // 验证死信记录的内容
            List<DeadLetterRecord> dlqRecords = deadLetterQueue.listAll();
            assertEquals(1, dlqRecords.size());

            DeadLetterRecord dlqRecord = dlqRecords.get(0);
            assertEquals("test-event-1", dlqRecord.getEventId());
            assertEquals("FileSink", dlqRecord.getComponent());
            assertEquals("WRITE", dlqRecord.getOperationType());
            assertEquals("PROCESSED_EVENT", dlqRecord.getDataType());
            assertEquals(maxRetries, dlqRecord.getRetryCount());
            assertNotNull(dlqRecord.getFailureReason());
            assertTrue(dlqRecord.getFailureReason().contains("persistent write failure"));
            assertNotNull(dlqRecord.getStackTrace());
            assertFalse(dlqRecord.isReprocessed());

            // 验证上下文信息
            Map<String, String> context = dlqRecord.getContext();
            assertNotNull(context);
            assertEquals("testdb", context.get("database"));
            assertEquals("users", context.get("table"));
            assertEquals("INSERT", context.get("eventType"));

        } finally {
            sinkWithDLQ.close();
        }
    }

    @Test
    void testNullEventHandling() throws Exception {
        // 创建一个mock sink
        AtomicInteger writeCount = new AtomicInteger(0);
        SinkFunction<ProcessedEvent> mockSink = new SinkFunction<ProcessedEvent>() {
            @Override
            public void invoke(ProcessedEvent value, Context context) throws Exception {
                writeCount.incrementAndGet();
            }
        };

        FileSinkWithDLQ sinkWithDLQ = new FileSinkWithDLQ(mockSink, dlqPath, 3);
        sinkWithDLQ.open(new Configuration());

        try {
            // 处理null事件
            sinkWithDLQ.invoke(null, null);

            // 验证没有调用delegate sink
            assertEquals(0, writeCount.get());

            // 验证没有记录进入死信队列
            assertEquals(0, deadLetterQueue.count());

        } finally {
            sinkWithDLQ.close();
        }
    }

    @Test
    void testMultipleEventsWithMixedResults() throws Exception {
        // 创建一个对特定事件失败的mock sink
        AtomicInteger successCount = new AtomicInteger(0);
        SinkFunction<ProcessedEvent> mockSink = new SinkFunction<ProcessedEvent>() {
            @Override
            public void invoke(ProcessedEvent value, Context context) throws Exception {
                if (value.getEventId().contains("fail")) {
                    throw new RuntimeException("Simulated failure for event: " + value.getEventId());
                }
                successCount.incrementAndGet();
            }
        };

        FileSinkWithDLQ sinkWithDLQ = new FileSinkWithDLQ(mockSink, dlqPath, 2);
        sinkWithDLQ.open(new Configuration());

        try {
            // 写入成功的事件
            sinkWithDLQ.invoke(createTestEvent("success-1"), null);
            sinkWithDLQ.invoke(createTestEvent("success-2"), null);

            // 写入失败的事件
            sinkWithDLQ.invoke(createTestEvent("fail-1"), null);

            // 再写入成功的事件
            sinkWithDLQ.invoke(createTestEvent("success-3"), null);

            // 验证成功写入的数量
            assertEquals(3, successCount.get());

            // 验证失败的记录进入了死信队列
            assertEquals(1, deadLetterQueue.count());

            List<DeadLetterRecord> dlqRecords = deadLetterQueue.listAll();
            assertEquals("fail-1", dlqRecords.get(0).getEventId());

        } finally {
            sinkWithDLQ.close();
        }
    }

    @Test
    void testRetryBackoff() throws Exception {
        // 创建一个总是失败的mock sink
        AtomicInteger attemptCount = new AtomicInteger(0);
        long[] attemptTimes = new long[4]; // 初始尝试 + 3次重试

        SinkFunction<ProcessedEvent> mockSink = new SinkFunction<ProcessedEvent>() {
            @Override
            public void invoke(ProcessedEvent value, Context context) throws Exception {
                int attempt = attemptCount.getAndIncrement();
                attemptTimes[attempt] = System.currentTimeMillis();
                throw new RuntimeException("Simulated failure");
            }
        };

        FileSinkWithDLQ sinkWithDLQ = new FileSinkWithDLQ(mockSink, dlqPath, 3);
        sinkWithDLQ.open(new Configuration());

        try {
            // 准备测试数据
            ProcessedEvent event = createTestEvent("test-event-1");

            // 执行写入
            sinkWithDLQ.invoke(event, null);

            // 验证尝试了4次
            assertEquals(4, attemptCount.get());

            // 验证重试间隔（指数退避）
            // 第1次重试: 2秒后
            long interval1 = attemptTimes[1] - attemptTimes[0];
            assertTrue(interval1 >= 2000, "First retry interval should be >= 2s, was " + interval1 + "ms");

            // 第2次重试: 4秒后
            long interval2 = attemptTimes[2] - attemptTimes[1];
            assertTrue(interval2 >= 4000, "Second retry interval should be >= 4s, was " + interval2 + "ms");

            // 第3次重试: 8秒后
            long interval3 = attemptTimes[3] - attemptTimes[2];
            assertTrue(interval3 >= 8000, "Third retry interval should be >= 8s, was " + interval3 + "ms");

        } finally {
            sinkWithDLQ.close();
        }
    }

    @Test
    void testDeadLetterQueueRecordContent() throws Exception {
        // 创建一个总是失败的mock sink
        SinkFunction<ProcessedEvent> mockSink = new SinkFunction<ProcessedEvent>() {
            @Override
            public void invoke(ProcessedEvent value, Context context) throws Exception {
                throw new RuntimeException("Test failure");
            }
        };

        FileSinkWithDLQ sinkWithDLQ = new FileSinkWithDLQ(mockSink, dlqPath, 1);
        sinkWithDLQ.open(new Configuration());

        try {
            // 准备测试数据
            ProcessedEvent event = createTestEvent("test-event-1");

            // 执行写入
            sinkWithDLQ.invoke(event, null);

            // 获取死信记录
            List<DeadLetterRecord> dlqRecords = deadLetterQueue.listAll();
            assertEquals(1, dlqRecords.size());

            DeadLetterRecord dlqRecord = dlqRecords.get(0);

            // 验证所有字段
            assertNotNull(dlqRecord.getRecordId());
            assertTrue(dlqRecord.getRecordId().startsWith("dlq_"));
            assertEquals("test-event-1", dlqRecord.getEventId());
            assertTrue(dlqRecord.getFailureTimestamp() > 0);
            assertEquals("Test failure", dlqRecord.getFailureReason());
            assertNotNull(dlqRecord.getStackTrace());
            assertTrue(dlqRecord.getStackTrace().contains("RuntimeException"));
            assertEquals("FileSink", dlqRecord.getComponent());
            assertEquals("WRITE", dlqRecord.getOperationType());
            assertEquals(1, dlqRecord.getRetryCount());
            assertNotNull(dlqRecord.getOriginalData());
            assertTrue(dlqRecord.getOriginalData().contains("test-event-1"));
            assertEquals("PROCESSED_EVENT", dlqRecord.getDataType());
            assertFalse(dlqRecord.isReprocessed());
            assertNull(dlqRecord.getReprocessTimestamp());

        } finally {
            sinkWithDLQ.close();
        }
    }

    /**
     * 创建测试用的ProcessedEvent
     */
    private ProcessedEvent createTestEvent(String eventId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "test");

        return ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .processTime(System.currentTimeMillis())
                .data(data)
                .partition("2025012812")
                .eventId(eventId)
                .build();
    }
}
