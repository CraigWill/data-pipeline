package com.realtime.pipeline.error;

import com.realtime.pipeline.model.DeadLetterRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件系统死信队列单元测试
 */
class FileBasedDeadLetterQueueTest {

    @TempDir
    Path tempDir;

    private FileBasedDeadLetterQueue deadLetterQueue;

    @BeforeEach
    void setUp() throws IOException {
        deadLetterQueue = new FileBasedDeadLetterQueue(tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (deadLetterQueue != null) {
            deadLetterQueue.close();
        }
    }

    @Test
    void testAddAndGetRecord() throws IOException {
        // 创建测试记录
        Map<String, String> context = new HashMap<>();
        context.put("outputPath", "/data/output");
        context.put("fileSize", "1024000");

        DeadLetterRecord record = DeadLetterRecord.builder()
                .recordId("test-record-1")
                .eventId("evt-12345")
                .failureTimestamp(System.currentTimeMillis())
                .failureReason("Failed to write file")
                .stackTrace("java.io.IOException: Disk full")
                .component("FileSink")
                .operationType("WRITE")
                .retryCount(3)
                .originalData("{\"eventType\":\"INSERT\"}")
                .dataType("PROCESSED_EVENT")
                .context(context)
                .reprocessed(false)
                .reprocessTimestamp(null)
                .build();

        // 添加记录
        deadLetterQueue.add(record);

        // 获取记录
        Optional<DeadLetterRecord> retrieved = deadLetterQueue.get("test-record-1");
        assertTrue(retrieved.isPresent());
        assertEquals("test-record-1", retrieved.get().getRecordId());
        assertEquals("evt-12345", retrieved.get().getEventId());
        assertEquals("FileSink", retrieved.get().getComponent());
        assertEquals("Failed to write file", retrieved.get().getFailureReason());
        assertEquals(3, retrieved.get().getRetryCount());
        assertFalse(retrieved.get().isReprocessed());
    }

    @Test
    void testGetNonExistentRecord() throws IOException {
        Optional<DeadLetterRecord> retrieved = deadLetterQueue.get("non-existent");
        assertFalse(retrieved.isPresent());
    }

    @Test
    void testAddNullRecord() {
        assertThrows(IllegalArgumentException.class, () -> {
            deadLetterQueue.add(null);
        });
    }

    @Test
    void testAddRecordWithNullId() {
        DeadLetterRecord record = DeadLetterRecord.builder()
                .recordId(null)
                .eventId("evt-12345")
                .failureTimestamp(System.currentTimeMillis())
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .retryCount(0)
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .context(new HashMap<>())
                .reprocessed(false)
                .reprocessTimestamp(null)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            deadLetterQueue.add(record);
        });
    }

    @Test
    void testListUnprocessed() throws IOException {
        // 添加未处理的记录
        DeadLetterRecord record1 = createTestRecord("record-1", false);
        DeadLetterRecord record2 = createTestRecord("record-2", false);
        DeadLetterRecord record3 = createTestRecord("record-3", true); // 已处理

        deadLetterQueue.add(record1);
        deadLetterQueue.add(record2);
        deadLetterQueue.add(record3);

        // 获取未处理的记录
        List<DeadLetterRecord> unprocessed = deadLetterQueue.listUnprocessed();
        assertEquals(2, unprocessed.size());
        assertTrue(unprocessed.stream().noneMatch(DeadLetterRecord::isReprocessed));
    }

    @Test
    void testListAll() throws IOException {
        // 添加多条记录
        deadLetterQueue.add(createTestRecord("record-1", false));
        deadLetterQueue.add(createTestRecord("record-2", true));
        deadLetterQueue.add(createTestRecord("record-3", false));

        // 获取所有记录
        List<DeadLetterRecord> allRecords = deadLetterQueue.listAll();
        assertEquals(3, allRecords.size());
    }

    @Test
    void testMarkAsReprocessed() throws IOException {
        // 添加未处理的记录
        DeadLetterRecord record = createTestRecord("record-1", false);
        deadLetterQueue.add(record);

        // 验证初始状态
        Optional<DeadLetterRecord> retrieved = deadLetterQueue.get("record-1");
        assertTrue(retrieved.isPresent());
        assertFalse(retrieved.get().isReprocessed());
        assertNull(retrieved.get().getReprocessTimestamp());

        // 标记为已处理
        deadLetterQueue.markAsReprocessed("record-1");

        // 验证更新后的状态
        retrieved = deadLetterQueue.get("record-1");
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isReprocessed());
        assertNotNull(retrieved.get().getReprocessTimestamp());
    }

    @Test
    void testMarkNonExistentRecordAsReprocessed() {
        assertThrows(IllegalArgumentException.class, () -> {
            deadLetterQueue.markAsReprocessed("non-existent");
        });
    }

    @Test
    void testDelete() throws IOException {
        // 添加记录
        DeadLetterRecord record = createTestRecord("record-1", false);
        deadLetterQueue.add(record);

        // 验证记录存在
        assertTrue(deadLetterQueue.get("record-1").isPresent());

        // 删除记录
        deadLetterQueue.delete("record-1");

        // 验证记录已删除
        assertFalse(deadLetterQueue.get("record-1").isPresent());
    }

    @Test
    void testDeleteNonExistentRecord() throws IOException {
        // 删除不存在的记录不应抛出异常
        assertDoesNotThrow(() -> {
            deadLetterQueue.delete("non-existent");
        });
    }

    @Test
    void testCount() throws IOException {
        assertEquals(0, deadLetterQueue.count());

        deadLetterQueue.add(createTestRecord("record-1", false));
        assertEquals(1, deadLetterQueue.count());

        deadLetterQueue.add(createTestRecord("record-2", false));
        assertEquals(2, deadLetterQueue.count());

        deadLetterQueue.delete("record-1");
        assertEquals(1, deadLetterQueue.count());
    }

    @Test
    void testClear() throws IOException {
        // 添加多条记录
        deadLetterQueue.add(createTestRecord("record-1", false));
        deadLetterQueue.add(createTestRecord("record-2", false));
        deadLetterQueue.add(createTestRecord("record-3", false));

        assertEquals(3, deadLetterQueue.count());

        // 清空队列
        deadLetterQueue.clear();

        assertEquals(0, deadLetterQueue.count());
        assertTrue(deadLetterQueue.listAll().isEmpty());
    }

    @Test
    void testConcurrentAdd() throws IOException, InterruptedException {
        // 测试并发添加记录
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    deadLetterQueue.add(createTestRecord("record-" + index, false));
                } catch (IOException e) {
                    fail("Failed to add record: " + e.getMessage());
                }
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证所有记录都已添加
        assertEquals(threadCount, deadLetterQueue.count());
    }

    @Test
    void testRecordPersistence() throws IOException {
        // 添加记录
        DeadLetterRecord record = createTestRecord("record-1", false);
        deadLetterQueue.add(record);

        // 关闭并重新打开队列
        deadLetterQueue.close();
        deadLetterQueue = new FileBasedDeadLetterQueue(tempDir.toString());

        // 验证记录仍然存在
        Optional<DeadLetterRecord> retrieved = deadLetterQueue.get("record-1");
        assertTrue(retrieved.isPresent());
        assertEquals("record-1", retrieved.get().getRecordId());
    }

    @Test
    void testCanReprocess() throws IOException {
        // 未处理的记录可以重新处理
        DeadLetterRecord unprocessed = createTestRecord("record-1", false);
        deadLetterQueue.add(unprocessed);
        
        Optional<DeadLetterRecord> retrieved = deadLetterQueue.get("record-1");
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().canReprocess());

        // 已处理的记录不能重新处理
        deadLetterQueue.markAsReprocessed("record-1");
        retrieved = deadLetterQueue.get("record-1");
        assertTrue(retrieved.isPresent());
        assertFalse(retrieved.get().canReprocess());
    }

    @Test
    void testGetFailureDuration() throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        DeadLetterRecord record = DeadLetterRecord.builder()
                .recordId("record-1")
                .eventId("evt-12345")
                .failureTimestamp(startTime)
                .failureReason("Test failure")
                .component("TestComponent")
                .operationType("TEST")
                .retryCount(0)
                .originalData("{}")
                .dataType("CHANGE_EVENT")
                .context(new HashMap<>())
                .reprocessed(false)
                .reprocessTimestamp(null)
                .build();

        deadLetterQueue.add(record);

        // 等待一小段时间
        Thread.sleep(100);

        Optional<DeadLetterRecord> retrieved = deadLetterQueue.get("record-1");
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().getFailureDuration() >= 100);
    }

    /**
     * 创建测试用的死信记录
     */
    private DeadLetterRecord createTestRecord(String recordId, boolean reprocessed) {
        Map<String, String> context = new HashMap<>();
        context.put("test", "value");

        return DeadLetterRecord.builder()
                .recordId(recordId)
                .eventId("evt-" + recordId)
                .failureTimestamp(System.currentTimeMillis())
                .failureReason("Test failure reason")
                .stackTrace("Test stack trace")
                .component("TestComponent")
                .operationType("TEST")
                .retryCount(0)
                .originalData("{\"test\":\"data\"}")
                .dataType("CHANGE_EVENT")
                .context(context)
                .reprocessed(reprocessed)
                .reprocessTimestamp(reprocessed ? System.currentTimeMillis() : null)
                .build();
    }
}
