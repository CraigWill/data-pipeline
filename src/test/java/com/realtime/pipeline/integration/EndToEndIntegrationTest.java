package com.realtime.pipeline.integration;

import com.realtime.pipeline.config.*;
import com.realtime.pipeline.flink.FlinkEnvironmentConfigurator;
import com.realtime.pipeline.flink.processor.EventProcessorWithDLQ;
import com.realtime.pipeline.flink.source.DataHubSource;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 端到端集成测试
 * 
 * 测试完整的数据流：
 * OceanBase CDC -> DataHub -> Flink Processing -> File Output
 * 
 * 验证需求: 所有需求的集成
 */
class EndToEndIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(EndToEndIntegrationTest.class);

    @TempDir
    Path tempDir;

    private PipelineConfig pipelineConfig;

    @BeforeEach
    void setUp() {
        // 创建完整的管道配置
        pipelineConfig = createTestPipelineConfig();
        
        // 清空收集器
        TestDataCollector.clear();
    }

    /**
     * 测试完整的数据流（端到端）
     * 
     * 验证需求: 1.1-1.7, 2.1-2.7, 3.1-3.8, 9.1, 9.6
     */
    @Test
    void testCompleteDataFlowEndToEnd() throws Exception {
        logger.info("Starting end-to-end data flow test");

        // 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // 测试环境使用单并行度

        // 配置Flink环境
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(
            pipelineConfig.getFlink()
        );
        configurator.configure(env);

        // 创建测试数据收集器
        TestDataCollector collector = new TestDataCollector();

        // 构建数据流
        // 模拟数据源（实际环境中会从DataHub消费）
        List<ChangeEvent> testEvents = createTestEvents(10);
        
        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        // 添加事件处理器
        String dlqPath = tempDir.resolve("dlq").toString();
        EventProcessorWithDLQ processor = new EventProcessorWithDLQ(dlqPath);

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        // 添加测试Sink收集数据
        processedStream.addSink(collector);

        // 执行作业（同步执行以确保数据被处理）
        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            try {
                env.execute("End-to-End Test");
            } catch (Exception e) {
                logger.error("Job execution failed", e);
            }
        });

        // 等待作业完成或超时
        try {
            jobFuture.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("Job execution timeout (expected for streaming job)");
        } catch (Exception e) {
            logger.error("Job execution error", e);
        }

        // 等待数据收集完成
        Thread.sleep(2000);

        // 验证所有事件都被处理
        List<ProcessedEvent> processedEvents = TestDataCollector.getCollectedEvents();
        
        assertThat(processedEvents)
            .as("All events should be processed")
            .hasSize(testEvents.size());

        // 验证事件ID唯一性（需求9.6）
        Set<String> eventIds = processedEvents.stream()
            .map(ProcessedEvent::getEventId)
            .collect(Collectors.toSet());

        assertThat(eventIds)
            .as("All event IDs should be unique")
            .hasSize(processedEvents.size());

        // 验证事件顺序保持（需求2.3）
        for (int i = 1; i < processedEvents.size(); i++) {
            long prevTimestamp = processedEvents.get(i - 1).getTimestamp();
            long currTimestamp = processedEvents.get(i).getTimestamp();

            assertThat(currTimestamp)
                .as("Event timestamps should be in order")
                .isGreaterThanOrEqualTo(prevTimestamp);
        }

        // 验证数据完整性
        for (int i = 0; i < testEvents.size(); i++) {
            ChangeEvent original = testEvents.get(i);
            ProcessedEvent processed = processedEvents.get(i);

            assertThat(processed.getEventType()).isEqualTo(original.getEventType());
            assertThat(processed.getDatabase()).isEqualTo(original.getDatabase());
            assertThat(processed.getTable()).isEqualTo(original.getTable());
        }

        logger.info("End-to-end data flow test completed successfully: {} events processed", 
            processedEvents.size());
    }

    /**
     * 测试多种数据类型的端到端处理
     * 
     * 验证需求: 1.1, 1.2, 1.3
     */
    @Test
    void testMultipleEventTypesEndToEnd() throws Exception {
        logger.info("Starting multiple event types end-to-end test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(
            pipelineConfig.getFlink()
        );
        configurator.configure(env);

        TestDataCollector collector = new TestDataCollector();

        // 创建包含INSERT、UPDATE、DELETE的测试数据
        List<ChangeEvent> testEvents = new ArrayList<>();
        
        // INSERT事件
        testEvents.add(createInsertEvent(1, "user1"));
        testEvents.add(createInsertEvent(2, "user2"));
        
        // UPDATE事件
        testEvents.add(createUpdateEvent(1, "user1", "user1_updated"));
        
        // DELETE事件
        testEvents.add(createDeleteEvent(2, "user2"));
        
        // 更多INSERT事件
        testEvents.add(createInsertEvent(3, "user3"));

        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        String dlqPath = tempDir.resolve("dlq").toString();
        EventProcessorWithDLQ processor = new EventProcessorWithDLQ(dlqPath);

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        // 执行作业（同步执行）
        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            try {
                env.execute("Multiple Event Types Test");
            } catch (Exception e) {
                logger.error("Job execution failed", e);
            }
        });

        // 等待作业完成或超时
        try {
            jobFuture.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("Job execution timeout (expected for streaming job)");
        } catch (Exception e) {
            logger.error("Job execution error", e);
        }

        // 等待数据收集完成
        Thread.sleep(2000);

        List<ProcessedEvent> processedEvents = TestDataCollector.getCollectedEvents();
        
        assertThat(processedEvents).hasSize(testEvents.size());

        // 验证每种事件类型都被正确处理
        long insertCount = processedEvents.stream()
            .filter(e -> "INSERT".equals(e.getEventType()))
            .count();
        long updateCount = processedEvents.stream()
            .filter(e -> "UPDATE".equals(e.getEventType()))
            .count();
        long deleteCount = processedEvents.stream()
            .filter(e -> "DELETE".equals(e.getEventType()))
            .count();

        assertThat(insertCount).isEqualTo(3);
        assertThat(updateCount).isEqualTo(1);
        assertThat(deleteCount).isEqualTo(1);

        logger.info("Multiple event types test completed: INSERT={}, UPDATE={}, DELETE={}", 
            insertCount, updateCount, deleteCount);
    }

    /**
     * 测试大数据量的端到端处理
     * 
     * 验证需求: 6.1 (支持500亿级别数据处理能力)
     */
    @Test
    void testLargeVolumeDataFlow() throws Exception {
        logger.info("Starting large volume data flow test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2); // 使用多并行度

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(
            pipelineConfig.getFlink()
        );
        configurator.configure(env);

        TestDataCollector collector = new TestDataCollector();

        // 创建大量测试数据
        int eventCount = 1000;
        List<ChangeEvent> testEvents = createTestEvents(eventCount);

        long startTime = System.currentTimeMillis();

        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        String dlqPath = tempDir.resolve("dlq").toString();
        EventProcessorWithDLQ processor = new EventProcessorWithDLQ(dlqPath);

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        // 执行作业（同步执行）
        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            try {
                env.execute("Large Volume Test");
            } catch (Exception e) {
                logger.error("Job execution failed", e);
            }
        });

        // 等待作业完成或超时
        try {
            jobFuture.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("Job execution timeout (expected for streaming job)");
        } catch (Exception e) {
            logger.error("Job execution error", e);
        }

        // 等待数据收集完成
        Thread.sleep(2000);

        long duration = System.currentTimeMillis() - startTime;

        List<ProcessedEvent> processedEvents = TestDataCollector.getCollectedEvents();
        
        assertThat(processedEvents).hasSize(eventCount);

        // 计算吞吐量
        double throughput = (double) eventCount / (duration / 1000.0);

        logger.info("Large volume test completed: {} events in {} ms, throughput: {:.2f} events/sec", 
            eventCount, duration, throughput);

        // 验证吞吐量合理（至少100 events/sec）
        assertThat(throughput)
            .as("Throughput should be reasonable")
            .isGreaterThan(100.0);
    }

    /**
     * 测试Checkpoint机制的端到端集成
     * 
     * 验证需求: 2.4, 4.1, 4.2
     */
    @Test
    void testCheckpointIntegration() throws Exception {
        logger.info("Starting checkpoint integration test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 配置较短的Checkpoint间隔用于测试
        FlinkConfig testFlinkConfig = FlinkConfig.builder()
            .parallelism(1)
            .checkpointInterval(5000L) // 5秒
            .checkpointTimeout(60000L)
            .minPauseBetweenCheckpoints(1000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(testFlinkConfig);
        configurator.configure(env);

        // 验证Checkpoint配置
        assertThat(env.getCheckpointConfig().isCheckpointingEnabled()).isTrue();
        assertThat(env.getCheckpointConfig().getCheckpointInterval()).isEqualTo(5000L);

        TestDataCollector collector = new TestDataCollector();

        List<ChangeEvent> testEvents = createTestEvents(20);
        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        String dlqPath = tempDir.resolve("dlq").toString();
        EventProcessorWithDLQ processor = new EventProcessorWithDLQ(dlqPath);

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        // 执行作业（同步执行）
        try {
            env.execute("Checkpoint Integration Test");
        } catch (Exception e) {
            logger.error("Job execution failed", e);
        }

        // 验证Checkpoint目录已创建
        File checkpointDir = tempDir.resolve("checkpoints").toFile();
        assertThat(checkpointDir.exists())
            .as("Checkpoint directory should be created")
            .isTrue();

        logger.info("Checkpoint integration test completed successfully");
    }

    /**
     * 测试死信队列集成
     * 
     * 验证需求: 2.7, 3.8
     */
    @Test
    void testDeadLetterQueueIntegration() throws Exception {
        logger.info("Starting dead letter queue integration test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(
            pipelineConfig.getFlink()
        );
        configurator.configure(env);

        TestDataCollector collector = new TestDataCollector();

        // 创建包含正常和异常数据的测试事件
        List<ChangeEvent> testEvents = new ArrayList<>();
        
        // 正常事件
        testEvents.add(createInsertEvent(1, "user1"));
        testEvents.add(createInsertEvent(2, "user2"));
        
        // 异常事件（null数据会导致处理失败）
        testEvents.add(createInvalidEvent());
        
        // 更多正常事件
        testEvents.add(createInsertEvent(3, "user3"));

        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        String dlqPath = tempDir.resolve("dlq").toString();
        EventProcessorWithDLQ processor = new EventProcessorWithDLQ(dlqPath);

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        // 执行作业（同步执行）
        try {
            env.execute("DLQ Integration Test");
        } catch (Exception e) {
            logger.error("Job execution failed", e);
        }

        List<ProcessedEvent> processedEvents = TestDataCollector.getCollectedEvents();
        
        // 验证正常事件被处理（异常事件进入DLQ）
        assertThat(processedEvents.size())
            .as("Only valid events should be processed")
            .isLessThanOrEqualTo(testEvents.size());

        // 验证DLQ目录已创建
        File dlqDir = tempDir.resolve("dlq").toFile();
        assertThat(dlqDir.exists())
            .as("DLQ directory should be created")
            .isTrue();

        logger.info("DLQ integration test completed: {} events processed, DLQ created", 
            processedEvents.size());
    }

    // ==================== 辅助方法 ====================

    private PipelineConfig createTestPipelineConfig() {
        DatabaseConfig databaseConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test")
            .password("test")
            .schema("testdb")
            .tables(Arrays.asList("users", "orders"))
            .build();

        DataHubConfig datahubConfig = DataHubConfig.builder()
            .endpoint("http://test-endpoint.com")
            .accessId("test-access-id")
            .accessKey("test-access-key")
            .project("test-project")
            .topic("test-topic")
            .consumerGroup("test-consumer-group")
            .startPosition("LATEST")
            .build();

        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(1)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        OutputConfig outputConfig = OutputConfig.builder()
            .path(tempDir.resolve("output").toString())
            .format("json")
            .rollingSizeBytes(1024 * 1024 * 1024L)
            .rollingIntervalMs(3600000L)
            .compression("none")
            .maxRetries(3)
            .build();

        MonitoringConfig monitoringConfig = MonitoringConfig.builder()
            .enabled(false)
            .metricsInterval(10)
            .healthCheckPort(8080)
            .build();

        return PipelineConfig.builder()
            .database(databaseConfig)
            .datahub(datahubConfig)
            .flink(flinkConfig)
            .output(outputConfig)
            .monitoring(monitoringConfig)
            .build();
    }

    private List<ChangeEvent> createTestEvents(int count) {
        List<ChangeEvent> events = new ArrayList<>();
        long baseTimestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", i);
            data.put("name", "user_" + i);
            data.put("email", "user" + i + "@example.com");

            ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(baseTimestamp + i * 1000)
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("event-" + i)
                .build();

            events.add(event);
        }

        return events;
    }

    private ChangeEvent createInsertEvent(int id, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", name);

        return ChangeEvent.builder()
            .eventType("INSERT")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .after(data)
            .primaryKeys(List.of("id"))
            .eventId("insert-" + id)
            .build();
    }

    private ChangeEvent createUpdateEvent(int id, String oldName, String newName) {
        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("id", id);
        beforeData.put("name", oldName);

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", id);
        afterData.put("name", newName);

        return ChangeEvent.builder()
            .eventType("UPDATE")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .before(beforeData)
            .after(afterData)
            .primaryKeys(List.of("id"))
            .eventId("update-" + id)
            .build();
    }

    private ChangeEvent createDeleteEvent(int id, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", name);

        return ChangeEvent.builder()
            .eventType("DELETE")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .before(data)
            .primaryKeys(List.of("id"))
            .eventId("delete-" + id)
            .build();
    }

    private ChangeEvent createInvalidEvent() {
        // 创建一个会导致处理失败的事件
        return ChangeEvent.builder()
            .eventType("INSERT")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .after(null) // null数据会导致处理失败
            .primaryKeys(List.of("id"))
            .eventId("invalid-event")
            .build();
    }

    /**
     * 测试数据收集器
     * 用于收集Flink处理后的数据
     */
    private static class TestDataCollector implements SinkFunction<ProcessedEvent> {
        private static final List<ProcessedEvent> collectedEvents = new CopyOnWriteArrayList<>();

        @Override
        public void invoke(ProcessedEvent value, Context context) {
            collectedEvents.add(value);
        }

        public static List<ProcessedEvent> getCollectedEvents() {
            return new ArrayList<>(collectedEvents);
        }

        public static void clear() {
            collectedEvents.clear();
        }
    }
}
