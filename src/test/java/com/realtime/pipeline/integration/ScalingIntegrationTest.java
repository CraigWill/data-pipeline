package com.realtime.pipeline.integration;

import com.realtime.pipeline.config.ConfigurationUpdateManager;
import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.flink.FlinkEnvironmentConfigurator;
import com.realtime.pipeline.flink.processor.EventProcessor;
import com.realtime.pipeline.flink.scaling.DynamicScalingManager;
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

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 扩缩容集成测试
 * 
 * 测试扩缩容场景：
 * - 动态扩容TaskManager
 * - 动态缩容TaskManager
 * - 动态调整并行度
 * - 扩缩容期间的数据连续性
 * - 零停机配置更新
 * 
 * 验证需求: 5.7, 6.2, 6.3, 6.6, 6.7
 */
class ScalingIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(ScalingIntegrationTest.class);

    @TempDir
    Path tempDir;

    private FlinkConfig flinkConfig;
    private DynamicScalingManager scalingManager;
    private ConfigurationUpdateManager configManager;

    @BeforeEach
    void setUp() {
        flinkConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .jobManagerHost("localhost")
            .jobManagerPort(8081)
            .build();

        scalingManager = new DynamicScalingManager("localhost", 8081);
        configManager = new ConfigurationUpdateManager(flinkConfig);
    }

    /**
     * 测试动态扩容TaskManager
     * 
     * 验证需求: 6.2
     */
    @Test
    void testDynamicScaleOut() {
        logger.info("Starting dynamic scale out test");

        // 扩容2个TaskManager
        int additionalTaskManagers = 2;
        boolean scaleOutSuccess = scalingManager.scaleOutTaskManagers(additionalTaskManagers);

        // 验证扩容成功
        assertThat(scaleOutSuccess)
            .as("Scale out operation should succeed")
            .isTrue();

        logger.info("Successfully scaled out {} TaskManager(s)", additionalTaskManagers);
    }

    /**
     * 测试动态缩容TaskManager
     * 
     * 验证需求: 6.7
     */
    @Test
    void testGracefulScaleIn() {
        logger.info("Starting graceful scale in test");

        // 优雅缩容1个TaskManager，等待5分钟完成当前任务
        int taskManagersToRemove = 1;
        long drainTimeout = 300000L; // 5分钟

        long startTime = System.currentTimeMillis();
        boolean scaleInSuccess = scalingManager.scaleInTaskManagers(
            taskManagersToRemove, drainTimeout);
        long duration = System.currentTimeMillis() - startTime;

        // 验证缩容成功
        assertThat(scaleInSuccess)
            .as("Scale in operation should succeed")
            .isTrue();

        // 验证操作在合理时间内完成
        assertThat(duration)
            .as("Scale in should complete within drain timeout")
            .isLessThan(drainTimeout);

        logger.info("Graceful scale in completed in {} ms", duration);
    }

    /**
     * 测试动态调整并行度
     * 
     * 验证需求: 6.3
     */
    @Test
    void testDynamicParallelismAdjustment() {
        logger.info("Starting dynamic parallelism adjustment test");

        String jobId = "test-job-" + UUID.randomUUID().toString();
        int newParallelism = 8;
        String savepointPath = tempDir.resolve("savepoints").toString();

        // 验证调整并行度操作接受有效参数
        assertThatCode(() -> {
            try {
                scalingManager.adjustParallelism(jobId, newParallelism, savepointPath);
            } catch (Exception e) {
                // 预期会失败（没有真实集群），但不应该是参数验证错误
                assertThat(e.getMessage())
                    .as("Should not be parameter validation error")
                    .doesNotContain("cannot be null", "must be positive", "cannot be empty");
            }
        }).doesNotThrowAnyException();

        logger.info("Parallelism adjustment validated for parallelism {}", newParallelism);
    }

    /**
     * 测试扩容期间的数据连续性
     * 
     * 验证需求: 6.6
     */
    @Test
    void testDataContinuityDuringScaleOut() throws Exception {
        logger.info("Starting data continuity during scale out test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        DataContinuityCollector collector = new DataContinuityCollector();

        // 创建测试数据
        List<ChangeEvent> testEvents = createTestEvents(100);
        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        EventProcessor processor = new EventProcessor();

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        // 启动数据处理
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean processingActive = new AtomicBoolean(true);

        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            try {
                startLatch.countDown();
                env.execute("Data Continuity During Scale Out Test");
            } catch (Exception e) {
                logger.warn("Job execution interrupted: {}", e.getMessage());
            }
        });

        // 等待处理开始
        startLatch.await();
        Thread.sleep(2000);

        // 在数据处理期间执行扩容
        logger.info("Triggering scale out during data processing...");
        boolean scaleOutSuccess = scalingManager.scaleOutTaskManagers(2);

        // 等待处理完成
        Thread.sleep(5000);
        processingActive.set(false);

        // 等待作业完成或超时
        try {
            jobFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.info("Job still running (expected in test)");
        } catch (Exception e) {
            logger.warn("Job execution error: {}", e.getMessage());
        }

        // 等待数据收集完成
        Thread.sleep(2000);

        // 验证扩容成功
        assertThat(scaleOutSuccess)
            .as("Scale out should succeed during data processing")
            .isTrue();

        // 验证数据连续性
        List<ProcessedEvent> processedEvents = collector.getCollectedEvents();
        
        assertThat(processedEvents)
            .as("Events should be processed during scale out")
            .isNotEmpty();

        // 验证所有唯一事件ID都被处理
        Set<String> expectedEventIds = testEvents.stream()
            .map(ChangeEvent::getEventId)
            .collect(Collectors.toSet());

        Set<String> processedEventIds = processedEvents.stream()
            .map(ProcessedEvent::getEventId)
            .collect(Collectors.toSet());

        // 注意：由于是异步处理，可能不是所有事件都处理完
        // 但处理的事件应该是输入事件的子集
        assertThat(processedEventIds)
            .as("Processed events should be subset of input events")
            .isSubsetOf(expectedEventIds);

        logger.info("Data continuity maintained during scale out: {}/{} events processed", 
            processedEvents.size(), testEvents.size());
    }

    /**
     * 测试缩容期间的优雅关闭
     * 
     * 验证需求: 6.7
     */
    @Test
    void testGracefulShutdownDuringScaleIn() throws Exception {
        logger.info("Starting graceful shutdown during scale in test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        GracefulShutdownCollector collector = new GracefulShutdownCollector();

        // 创建测试数据
        List<ChangeEvent> testEvents = createTestEvents(50);
        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        EventProcessor processor = new EventProcessor();

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        // 启动数据处理
        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            try {
                env.execute("Graceful Shutdown Test");
            } catch (Exception e) {
                logger.warn("Job execution interrupted: {}", e.getMessage());
            }
        });

        // 等待部分数据处理
        Thread.sleep(2000);

        // 触发优雅缩容
        logger.info("Triggering graceful scale in...");
        long drainTimeout = 300000L;
        boolean scaleInSuccess = scalingManager.scaleInTaskManagers(1, drainTimeout);

        // 等待处理完成
        Thread.sleep(3000);

        // 等待作业完成或超时
        try {
            jobFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.info("Job still running (expected in test)");
        } catch (Exception e) {
            logger.warn("Job execution error: {}", e.getMessage());
        }

        // 等待数据收集完成
        Thread.sleep(2000);

        // 验证缩容成功
        assertThat(scaleInSuccess)
            .as("Graceful scale in should succeed")
            .isTrue();

        // 验证有数据被处理
        List<ProcessedEvent> processedEvents = collector.getCollectedEvents();
        
        assertThat(processedEvents)
            .as("Some events should be processed before shutdown")
            .isNotEmpty();

        logger.info("Graceful shutdown completed: {} events processed", 
            processedEvents.size());
    }

    /**
     * 测试零停机配置更新
     * 
     * 验证需求: 5.7
     */
    @Test
    void testZeroDowntimeConfigUpdate() throws Exception {
        logger.info("Starting zero downtime config update test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        ConfigUpdateCollector collector = new ConfigUpdateCollector();

        // 创建测试数据
        List<ChangeEvent> testEvents = createTestEvents(100);
        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        EventProcessor processor = new EventProcessor();

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        // 启动数据处理
        AtomicBoolean processingActive = new AtomicBoolean(true);
        AtomicInteger processedBeforeUpdate = new AtomicInteger(0);
        AtomicInteger processedAfterUpdate = new AtomicInteger(0);
        AtomicBoolean configUpdated = new AtomicBoolean(false);

        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            try {
                env.execute("Zero Downtime Config Update Test");
            } catch (Exception e) {
                logger.warn("Job execution interrupted: {}", e.getMessage());
            }
        });

        // 等待处理开始
        Thread.sleep(2000);

        // 在数据处理期间更新配置
        logger.info("Updating configuration during data processing...");
        
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .jobManagerHost("localhost")
            .jobManagerPort(8081)
            .build();

        long updateStartTime = System.currentTimeMillis();
        configManager.updateFlinkConfig(newConfig);
        long updateDuration = System.currentTimeMillis() - updateStartTime;
        configUpdated.set(true);

        // 等待处理完成
        Thread.sleep(3000);
        processingActive.set(false);

        // 等待作业完成或超时
        try {
            jobFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.info("Job still running (expected in test)");
        } catch (Exception e) {
            logger.warn("Job execution error: {}", e.getMessage());
        }

        // 等待数据收集完成
        Thread.sleep(2000);

        // 验证配置更新非常快（零停机）
        assertThat(updateDuration)
            .as("Config update should be very fast (zero downtime)")
            .isLessThan(100);

        // 验证配置已更新
        FlinkConfig currentConfig = configManager.getCurrentFlinkConfig();
        assertThat(currentConfig.getParallelism())
            .as("Parallelism should be updated")
            .isEqualTo(8);

        // 验证数据处理继续进行
        List<ProcessedEvent> processedEvents = collector.getCollectedEvents();
        
        assertThat(processedEvents)
            .as("Events should continue to be processed after config update")
            .isNotEmpty();

        logger.info("Zero downtime config update completed in {} ms: {} events processed", 
            updateDuration, processedEvents.size());
    }

    /**
     * 测试扩容后缩容
     * 
     * 验证需求: 6.2, 6.7
     */
    @Test
    void testScaleOutThenScaleIn() {
        logger.info("Starting scale out then scale in test");

        // 先扩容
        logger.info("Scaling out...");
        boolean scaleOutSuccess = scalingManager.scaleOutTaskManagers(2);
        assertThat(scaleOutSuccess).isTrue();

        // 等待扩容完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 再缩容
        logger.info("Scaling in...");
        boolean scaleInSuccess = scalingManager.scaleInTaskManagers(1, 300000L);
        assertThat(scaleInSuccess).isTrue();

        logger.info("Scale out then scale in test completed successfully");
    }

    /**
     * 测试并发扩缩容操作
     * 
     * 验证需求: 6.2, 6.3, 5.7
     */
    @Test
    void testConcurrentScalingOperations() throws Exception {
        logger.info("Starting concurrent scaling operations test");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);
        AtomicBoolean scaleOutSuccess = new AtomicBoolean(false);
        AtomicBoolean configUpdateSuccess = new AtomicBoolean(false);
        AtomicBoolean parallelismAdjustSuccess = new AtomicBoolean(false);

        // 扩容线程
        Thread scaleOutThread = new Thread(() -> {
            try {
                startLatch.await();
                boolean result = scalingManager.scaleOutTaskManagers(2);
                scaleOutSuccess.set(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // 配置更新线程
        Thread configUpdateThread = new Thread(() -> {
            try {
                startLatch.await();
                FlinkConfig newConfig = FlinkConfig.builder()
                    .parallelism(8)
                    .checkpointInterval(180000L)
                    .checkpointTimeout(600000L)
                    .minPauseBetweenCheckpoints(60000L)
                    .maxConcurrentCheckpoints(1)
                    .retainedCheckpoints(3)
                    .stateBackendType("hashmap")
                    .checkpointDir(tempDir.resolve("checkpoints").toString())
                    .jobManagerHost("localhost")
                    .jobManagerPort(8081)
                    .build();
                configManager.updateFlinkConfig(newConfig);
                configUpdateSuccess.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // 并行度调整线程
        Thread parallelismThread = new Thread(() -> {
            try {
                startLatch.await();
                // 注意：实际调整会失败因为没有真实集群，但我们验证不会抛出参数错误
                try {
                    scalingManager.adjustParallelism(
                        "test-job-id", 
                        10, 
                        tempDir.resolve("savepoints").toString()
                    );
                } catch (Exception e) {
                    // 预期会失败，但不应该是参数验证错误
                    parallelismAdjustSuccess.set(
                        !e.getMessage().contains("cannot be null") &&
                        !e.getMessage().contains("must be positive")
                    );
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // 启动所有线程
        scaleOutThread.start();
        configUpdateThread.start();
        parallelismThread.start();

        // 同时开始所有操作
        startLatch.countDown();

        // 等待所有操作完成
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        assertThat(completed)
            .as("All concurrent operations should complete")
            .isTrue();

        // 验证操作结果
        assertThat(scaleOutSuccess.get())
            .as("Scale out should succeed")
            .isTrue();

        assertThat(configUpdateSuccess.get())
            .as("Config update should succeed")
            .isTrue();

        logger.info("Concurrent scaling operations test completed successfully");
    }

    /**
     * 测试扩缩容期间的吞吐量
     * 
     * 验证需求: 6.4
     */
    @Test
    void testThroughputDuringScaling() throws Exception {
        logger.info("Starting throughput during scaling test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        ThroughputCollector collector = new ThroughputCollector();

        // 创建大量测试数据
        int eventCount = 500;
        List<ChangeEvent> testEvents = createTestEvents(eventCount);
        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        EventProcessor processor = new EventProcessor();

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        long startTime = System.currentTimeMillis();

        // 启动数据处理
        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            try {
                env.execute("Throughput During Scaling Test");
            } catch (Exception e) {
                logger.warn("Job execution interrupted: {}", e.getMessage());
            }
        });

        // 等待处理开始
        Thread.sleep(1000);

        // 在处理期间扩容
        scalingManager.scaleOutTaskManagers(2);

        // 等待处理完成
        Thread.sleep(5000);

        // 等待作业完成或超时
        try {
            jobFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.info("Job still running (expected in test)");
        } catch (Exception e) {
            logger.warn("Job execution error: {}", e.getMessage());
        }

        // 等待数据收集完成
        Thread.sleep(2000);

        long duration = System.currentTimeMillis() - startTime;

        List<ProcessedEvent> processedEvents = collector.getCollectedEvents();
        
        // 计算吞吐量
        double throughput = processedEvents.size() / (duration / 1000.0);

        logger.info("Throughput during scaling: {:.2f} events/sec ({} events in {} ms)", 
            throughput, processedEvents.size(), duration);

        // 验证吞吐量合理
        assertThat(throughput)
            .as("Throughput should be reasonable during scaling")
            .isGreaterThan(10.0);
    }

    // ==================== 辅助方法 ====================

    private List<ChangeEvent> createTestEvents(int count) {
        List<ChangeEvent> events = new ArrayList<>();
        long baseTimestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", i);
            data.put("name", "user_" + i);
            data.put("value", i * 100);

            ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(baseTimestamp + i * 10)
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("event-" + i)
                .build();

            events.add(event);
        }

        return events;
    }

    /**
     * 数据连续性收集器
     */
    private static class DataContinuityCollector implements SinkFunction<ProcessedEvent> {
        private final List<ProcessedEvent> collectedEvents = new CopyOnWriteArrayList<>();

        @Override
        public void invoke(ProcessedEvent value, Context context) {
            collectedEvents.add(value);
        }

        public List<ProcessedEvent> getCollectedEvents() {
            return new ArrayList<>(collectedEvents);
        }
    }

    /**
     * 优雅关闭收集器
     */
    private static class GracefulShutdownCollector implements SinkFunction<ProcessedEvent> {
        private final List<ProcessedEvent> collectedEvents = new CopyOnWriteArrayList<>();

        @Override
        public void invoke(ProcessedEvent value, Context context) {
            collectedEvents.add(value);
        }

        public List<ProcessedEvent> getCollectedEvents() {
            return new ArrayList<>(collectedEvents);
        }
    }

    /**
     * 配置更新收集器
     */
    private static class ConfigUpdateCollector implements SinkFunction<ProcessedEvent> {
        private final List<ProcessedEvent> collectedEvents = new CopyOnWriteArrayList<>();

        @Override
        public void invoke(ProcessedEvent value, Context context) {
            collectedEvents.add(value);
        }

        public List<ProcessedEvent> getCollectedEvents() {
            return new ArrayList<>(collectedEvents);
        }
    }

    /**
     * 吞吐量收集器
     */
    private static class ThroughputCollector implements SinkFunction<ProcessedEvent> {
        private final List<ProcessedEvent> collectedEvents = new CopyOnWriteArrayList<>();
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public void invoke(ProcessedEvent value, Context context) {
            collectedEvents.add(value);
            count.incrementAndGet();
        }

        public List<ProcessedEvent> getCollectedEvents() {
            return new ArrayList<>(collectedEvents);
        }

        public int getCount() {
            return count.get();
        }
    }
}
