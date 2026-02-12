package com.realtime.pipeline.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.flink.processor.EventProcessorWithDLQ;
import com.realtime.pipeline.flink.sink.FileSinkWithDLQ;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 错误处理基于属性的测试
 * 使用jqwik进行属性测试，验证错误处理和死信队列的正确性属性
 * 
 * Feature: realtime-data-pipeline
 */
class ErrorHandlingPropertyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Feature: realtime-data-pipeline, Property 10: 处理失败数据隔离
     * 
     * **Validates: Requirements 2.7, 3.8**
     * 
     * 对于任何处理失败的数据记录，系统应该将其发送到死信队列而不影响其他数据处理
     * 
     * 测试策略:
     * - 生成包含正常和异常数据的混合事件序列
     * - 使用会对特定数据抛出异常的处理器
     * - 验证失败的记录被发送到死信队列
     * - 验证成功的记录正常处理
     * - 验证失败不影响后续记录的处理
     */
    @Property(tries = 20)
    void processingFailureDataIsolation(
            @ForAll("mixedEventSequences") List<ChangeEvent> events,
            @ForAll @IntRange(min = 1, max = 5) int failureRate) throws Exception {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("dlq-test-");
        
        try {
            // 创建死信队列
            String dlqPath = tempDir.resolve("dlq").toString();
            FileBasedDeadLetterQueue dlq = new FileBasedDeadLetterQueue(dlqPath);
            
            try {
                // 创建会对特定条件抛出异常的处理器
                FailureInjectingProcessor processor = new FailureInjectingProcessor(dlqPath, failureRate);
                processor.open(null);
                
                // 跟踪处理结果
                int successCount = 0;
                int failureCount = 0;
                Set<String> processedEventIds = new HashSet<>();
                
                // 处理所有事件
                for (ChangeEvent event : events) {
                    try {
                        ProcessedEvent result = processor.map(event);
                        if (result != null) {
                            successCount++;
                            processedEventIds.add(result.getEventId());
                        } else {
                            // null表示处理失败，应该进入死信队列
                            failureCount++;
                        }
                    } catch (Exception e) {
                        // 不应该抛出异常到外部
                        fail("Processing should not throw exceptions to caller: " + e.getMessage());
                    }
                }
                
                processor.close();
                
                // 验证死信队列中的记录数
                long dlqCount = dlq.count();
                assertEquals(failureCount, dlqCount,
                        "Dead letter queue should contain exactly the failed records");
                
                // 验证死信队列中的记录
                List<DeadLetterRecord> dlqRecords = dlq.listAll();
                assertEquals(failureCount, dlqRecords.size(),
                        "Should have correct number of DLQ records");
                
                // 验证每个死信记录的完整性
                for (DeadLetterRecord record : dlqRecords) {
                    assertNotNull(record.getRecordId(), "DLQ record should have recordId");
                    assertNotNull(record.getEventId(), "DLQ record should have eventId");
                    assertNotNull(record.getFailureReason(), "DLQ record should have failure reason");
                    assertNotNull(record.getComponent(), "DLQ record should have component");
                    assertNotNull(record.getOriginalData(), "DLQ record should have original data");
                    assertEquals("EventProcessor", record.getComponent(),
                            "Component should be EventProcessor");
                    assertFalse(record.isReprocessed(), "New DLQ record should not be marked as reprocessed");
                    
                    // 验证原始数据可以反序列化
                    assertDoesNotThrow(() -> {
                        ChangeEvent originalEvent = objectMapper.readValue(
                                record.getOriginalData(), ChangeEvent.class);
                        assertNotNull(originalEvent, "Original data should be deserializable");
                    });
                }
                
                // 验证成功处理的记录不在死信队列中
                // 注意: 由于事件ID可能重复（特别是在属性测试的shrinking过程中），
                // 我们需要更仔细地验证。我们检查DLQ中的记录数量是否等于失败数量。
                Set<String> dlqEventIds = new HashSet<>();
                for (DeadLetterRecord record : dlqRecords) {
                    dlqEventIds.add(record.getEventId());
                }
                
                // 如果所有事件ID都是唯一的，则验证成功处理的记录不在DLQ中
                Set<String> allEventIds = events.stream()
                        .map(ChangeEvent::getEventId)
                        .collect(Collectors.toSet());
                
                if (allEventIds.size() == events.size()) {
                    // 所有事件ID都是唯一的，可以安全地进行检查
                    for (String processedId : processedEventIds) {
                        assertFalse(dlqEventIds.contains(processedId),
                                "Successfully processed events should not be in DLQ");
                    }
                } else {
                    // 有重复的事件ID，只验证数量
                    // 这种情况通常发生在属性测试的shrinking过程中
                    assertTrue(dlqRecords.size() <= failureCount,
                            "DLQ should not have more records than failures");
                }
                
                // 验证总数正确
                assertEquals(events.size(), successCount + failureCount,
                        "All events should be either processed or failed");
                
                // 验证至少有一些成功和一些失败（如果事件数量足够）
                if (events.size() >= 10 && failureRate > 1) {
                    assertTrue(successCount > 0, "Should have some successful processing");
                    assertTrue(failureCount > 0, "Should have some failures for testing");
                }
                
            } finally {
                dlq.close();
            }
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }

    /**
     * Feature: realtime-data-pipeline, Property 10: 处理失败数据隔离 (Sink层面)
     * 
     * **Validates: Requirements 2.7, 3.8**
     * 
     * 对于任何写入失败的数据记录，系统应该将其发送到死信队列而不影响其他数据写入
     * 
     * 测试策略:
     * - 生成ProcessedEvent序列
     * - 使用会对特定数据抛出异常的Sink
     * - 验证失败的记录被发送到死信队列
     * - 验证成功的记录正常写入
     * - 验证失败不影响后续记录的写入
     */
    @Property(tries = 20)
    void sinkFailureDataIsolation(
            @ForAll("processedEventSequences") List<ProcessedEvent> events,
            @ForAll @IntRange(min = 1, max = 5) int failureRate) throws Exception {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("dlq-test-");
        
        try {
            // 创建死信队列
            String dlqPath = tempDir.resolve("dlq").toString();
            FileBasedDeadLetterQueue dlq = new FileBasedDeadLetterQueue(dlqPath);
            
            try {
                // 创建会对特定条件抛出异常的Sink
                FailureInjectingSink delegateSink = new FailureInjectingSink(failureRate);
                FileSinkWithDLQ sinkWithDLQ = new FileSinkWithDLQ(delegateSink, dlqPath, 0); // maxRetries=0 for faster testing
                sinkWithDLQ.open(null);
                
                // 处理所有事件
                for (ProcessedEvent event : events) {
                    try {
                        sinkWithDLQ.invoke(event, null);
                    } catch (Exception e) {
                        // 不应该抛出异常到外部
                        fail("Sink should not throw exceptions to caller: " + e.getMessage());
                    }
                }
                
                sinkWithDLQ.close();
                
                // 验证死信队列中的记录
                List<DeadLetterRecord> dlqRecords = dlq.listAll();
                int failureCount = delegateSink.getFailureCount();
                int successCount = delegateSink.getSuccessCount();
                
                assertEquals(failureCount, dlqRecords.size(),
                        "Dead letter queue should contain exactly the failed records");
                
                // 验证每个死信记录的完整性
                for (DeadLetterRecord record : dlqRecords) {
                    assertNotNull(record.getRecordId(), "DLQ record should have recordId");
                    assertNotNull(record.getEventId(), "DLQ record should have eventId");
                    assertNotNull(record.getFailureReason(), "DLQ record should have failure reason");
                    assertNotNull(record.getComponent(), "DLQ record should have component");
                    assertNotNull(record.getOriginalData(), "DLQ record should have original data");
                    assertEquals("FileSink", record.getComponent(),
                            "Component should be FileSink");
                    assertEquals("WRITE", record.getOperationType(),
                            "Operation type should be WRITE");
                    assertFalse(record.isReprocessed(), "New DLQ record should not be marked as reprocessed");
                    
                    // 验证原始数据可以反序列化
                    assertDoesNotThrow(() -> {
                        ProcessedEvent originalEvent = objectMapper.readValue(
                                record.getOriginalData(), ProcessedEvent.class);
                        assertNotNull(originalEvent, "Original data should be deserializable");
                    });
                }
                
                // 验证总数正确
                assertEquals(events.size(), successCount + failureCount,
                        "All events should be either written or failed");
                
                // 验证至少有一些成功和一些失败（如果事件数量足够）
                if (events.size() >= 10 && failureRate > 1) {
                    assertTrue(successCount > 0, "Should have some successful writes");
                    assertTrue(failureCount > 0, "Should have some failures for testing");
                }
                
            } finally {
                dlq.close();
            }
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }

    /**
     * 属性测试: 死信队列记录包含完整的上下文信息
     * 
     * 验证死信队列记录包含足够的信息用于调试和重新处理
     */
    @Property(tries = 10)
    void dlqRecordsContainCompleteContext(
            @ForAll("changeEvents") List<ChangeEvent> events) throws Exception {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("dlq-test-");
        
        try {
            String dlqPath = tempDir.resolve("dlq").toString();
            FileBasedDeadLetterQueue dlq = new FileBasedDeadLetterQueue(dlqPath);
            
            try {
                // 创建总是失败的处理器
                AlwaysFailingProcessor processor = new AlwaysFailingProcessor(dlqPath);
                processor.open(null);
                
                // 处理所有事件（都会失败）
                for (ChangeEvent event : events) {
                    processor.map(event);
                }
                
                processor.close();
                
                // 验证所有记录都在死信队列中
                List<DeadLetterRecord> dlqRecords = dlq.listAll();
                assertEquals(events.size(), dlqRecords.size(),
                        "All failed events should be in DLQ");
                
                // 验证每个DLQ记录的上下文信息正确
                // 注意: 由于事件ID可能重复（特别是在属性测试的shrinking过程中），
                // 我们不能使用eventId作为唯一键。相反，我们验证每个DLQ记录的上下文
                // 与其原始数据中的信息一致。
                for (DeadLetterRecord dlqRecord : dlqRecords) {
                    // 验证基本字段
                    assertNotNull(dlqRecord.getRecordId());
                    assertNotNull(dlqRecord.getFailureTimestamp());
                    assertNotNull(dlqRecord.getFailureReason());
                    assertNotNull(dlqRecord.getStackTrace());
                    assertNotNull(dlqRecord.getEventId());
                    
                    // 验证上下文信息与原始数据一致
                    Map<String, String> context = dlqRecord.getContext();
                    assertNotNull(context, "Context should not be null");
                    
                    // 从原始数据中反序列化事件
                    ChangeEvent deserializedEvent = objectMapper.readValue(
                            dlqRecord.getOriginalData(), ChangeEvent.class);
                    
                    // 验证上下文信息与反序列化的事件一致
                    assertEquals(deserializedEvent.getDatabase(), context.get("database"),
                            "Context database should match original event");
                    assertEquals(deserializedEvent.getTable(), context.get("table"),
                            "Context table should match original event");
                    assertEquals(deserializedEvent.getEventType(), context.get("eventType"),
                            "Context eventType should match original event");
                }
                
            } finally {
                dlq.close();
            }
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }

    /**
     * 属性测试: 并发处理失败不会导致数据丢失
     * 
     * 验证在并发场景下，失败的记录都能正确进入死信队列
     */
    @Property(tries = 30)
    void concurrentFailuresDoNotLoseData(
            @ForAll("changeEvents") List<ChangeEvent> events) throws Exception {
        
        if (events.size() < 10) {
            return; // 跳过太小的测试用例
        }
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("dlq-test-");
        
        try {
            String dlqPath = tempDir.resolve("dlq").toString();
            FileBasedDeadLetterQueue dlq = new FileBasedDeadLetterQueue(dlqPath);
            
            try {
                // 创建总是失败的处理器
                AlwaysFailingProcessor processor = new AlwaysFailingProcessor(dlqPath);
                processor.open(null);
                
                // 并发处理事件
                AtomicInteger processedCount = new AtomicInteger(0);
                List<Thread> threads = new ArrayList<>();
                
                for (ChangeEvent event : events) {
                    Thread thread = new Thread(() -> {
                        try {
                            processor.map(event);
                            processedCount.incrementAndGet();
                        } catch (Exception e) {
                            // 忽略异常
                        }
                    });
                    threads.add(thread);
                    thread.start();
                }
                
                // 等待所有线程完成
                for (Thread thread : threads) {
                    thread.join();
                }
                
                processor.close();
                
                // 验证所有记录都在死信队列中
                long dlqCount = dlq.count();
                assertEquals(events.size(), dlqCount,
                        "All failed events should be in DLQ even with concurrent processing");
                
                // 验证没有重复的记录ID
                List<DeadLetterRecord> dlqRecords = dlq.listAll();
                Set<String> recordIds = new HashSet<>();
                for (DeadLetterRecord record : dlqRecords) {
                    assertTrue(recordIds.add(record.getRecordId()),
                            "DLQ record IDs should be unique");
                }
                
            } finally {
                dlq.close();
            }
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // 忽略删除失败
                        }
                    });
        }
    }

    // ==================== 数据生成器 ====================

    /**
     * 生成混合事件序列（包含正常和可能导致失败的数据）
     */
    @Provide
    Arbitrary<List<ChangeEvent>> mixedEventSequences() {
        return changeEvent().list().ofMinSize(10).ofMaxSize(50);
    }

    /**
     * 生成ProcessedEvent序列
     */
    @Provide
    Arbitrary<List<ProcessedEvent>> processedEventSequences() {
        return processedEvent().list().ofMinSize(10).ofMaxSize(50);
    }

    /**
     * 生成变更事件列表
     */
    @Provide
    Arbitrary<List<ChangeEvent>> changeEvents() {
        return changeEvent().list().ofMinSize(5).ofMaxSize(30);
    }

    /**
     * 生成单个变更事件
     */
    @Provide
    Arbitrary<ChangeEvent> changeEvent() {
        return Combinators.combine(
                eventType(),
                databaseName(),
                tableName(),
                timestamp(),
                eventData(),
                eventId()
        ).as((type, db, table, ts, data, id) -> {
            if ("DELETE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, null, List.of("id"), id);
            } else if ("UPDATE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, data, List.of("id"), id);
            } else {
                return new ChangeEvent(type, db, table, ts, null, data, List.of("id"), id);
            }
        });
    }

    /**
     * 生成单个ProcessedEvent
     */
    @Provide
    Arbitrary<ProcessedEvent> processedEvent() {
        return Combinators.combine(
                eventType(),
                databaseName(),
                tableName(),
                timestamp(),
                eventData(),
                eventId(),
                partition()
        ).as((type, db, table, ts, data, id, part) -> 
                ProcessedEvent.builder()
                        .eventType(type)
                        .database(db)
                        .table(table)
                        .timestamp(ts)
                        .processTime(System.currentTimeMillis())
                        .data(data)
                        .partition(part)
                        .eventId(id != null && !id.isEmpty() ? id : "evt-" + UUID.randomUUID().toString().substring(0, 8))
                        .build()
        );
    }

    @Provide
    Arbitrary<String> eventType() {
        return Arbitraries.of("INSERT", "UPDATE", "DELETE");
    }

    @Provide
    Arbitrary<String> databaseName() {
        return Arbitraries.of("testdb", "proddb", "devdb");
    }

    @Provide
    Arbitrary<String> tableName() {
        return Arbitraries.of("users", "orders", "products", "customers");
    }

    @Provide
    Arbitrary<Long> timestamp() {
        long now = System.currentTimeMillis();
        return Arbitraries.longs().between(now - 86400000, now);
    }

    @Provide
    Arbitrary<Map<String, Object>> eventData() {
        return Arbitraries.integers().between(1, 10000).map(id -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", "name_" + id);
            data.put("value", id * 100);
            return data;
        });
    }

    @Provide
    Arbitrary<String> eventId() {
        return Arbitraries.strings().alpha().ofLength(10);
    }

    @Provide
    Arbitrary<String> partition() {
        return Arbitraries.integers().between(2024010100, 2025123123)
                .map(String::valueOf);
    }

    // ==================== 测试辅助类 ====================

    /**
     * 注入失败的处理器 - 根据failureRate决定是否失败
     * 
     * 这个处理器通过在generatePartition方法中抛出异常来模拟失败，
     * 这样父类的map()方法中的try-catch会捕获异常并发送到DLQ
     */
    private static class FailureInjectingProcessor extends EventProcessorWithDLQ {
        private final int failureRate;
        private int counter = 0;

        public FailureInjectingProcessor(String dlqPath, int failureRate) {
            super(dlqPath);
            this.failureRate = failureRate;
        }

        @Override
        protected String generatePartition(long timestamp) {
            counter++;
            // 每failureRate个事件中有1个失败
            if (counter % failureRate == 0) {
                throw new RuntimeException("Simulated processing failure");
            }
            return super.generatePartition(timestamp);
        }
    }

    /**
     * 总是失败的处理器 - 用于测试所有记录都进入死信队列
     * 
     * 这个处理器通过在generatePartition方法中抛出异常来模拟失败，
     * 这样父类的map()方法中的try-catch会捕获异常并发送到DLQ
     */
    private static class AlwaysFailingProcessor extends EventProcessorWithDLQ {
        public AlwaysFailingProcessor(String dlqPath) {
            super(dlqPath);
        }

        @Override
        protected String generatePartition(long timestamp) {
            throw new RuntimeException("Simulated processing failure");
        }
    }

    /**
     * 注入失败的Sink - 根据failureRate决定是否失败
     */
    private static class FailureInjectingSink implements SinkFunction<ProcessedEvent> {
        private final int failureRate;
        private final AtomicInteger counter = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);

        public FailureInjectingSink(int failureRate) {
            this.failureRate = failureRate;
        }

        @Override
        public void invoke(ProcessedEvent value, Context context) throws Exception {
            int count = counter.incrementAndGet();
            // 每failureRate个事件中有1个失败
            if (count % failureRate == 0) {
                failureCount.incrementAndGet();
                throw new IOException("Simulated write failure for event: " + value.getEventId());
            }
            successCount.incrementAndGet();
        }

        public int getSuccessCount() {
            return successCount.get();
        }

        public int getFailureCount() {
            return failureCount.get();
        }
    }
}
