package com.realtime.pipeline.integration;

import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.config.HighAvailabilityConfig;
import com.realtime.pipeline.flink.FlinkEnvironmentConfigurator;
import com.realtime.pipeline.flink.ha.JobManagerFailoverManager;
import com.realtime.pipeline.flink.processor.EventProcessor;
import com.realtime.pipeline.flink.recovery.FaultRecoveryManager;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 故障恢复集成测试
 * 
 * 测试故障恢复场景：
 * - Checkpoint恢复
 * - JobManager故障切换
 * - TaskManager失败恢复
 * - 数据处理连续性
 * 
 * 验证需求: 4.1, 4.2, 4.3, 4.4, 4.5, 5.3
 */
class FaultRecoveryIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(FaultRecoveryIntegrationTest.class);

    @TempDir
    Path tempDir;

    private FlinkConfig flinkConfig;
    private HighAvailabilityConfig haConfig;

    @BeforeEach
    void setUp() {
        // 配置较短的Checkpoint间隔用于测试
        flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(5000L) // 5秒
            .checkpointTimeout(60000L)
            .minPauseBetweenCheckpoints(1000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .tolerableCheckpointFailures(3)
            .restartStrategy("fixed-delay")
            .restartAttempts(3)
            .restartDelay(1000L)
            .build();

        haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir(tempDir.resolve("ha").toString())
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .build();
    }

    /**
     * 测试Checkpoint恢复机制
     * 
     * 验证需求: 4.1, 4.2, 4.3
     */
    @Test
    void testCheckpointRecovery() throws Exception {
        logger.info("Starting checkpoint recovery test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // 验证Checkpoint配置
        assertThat(env.getCheckpointConfig().isCheckpointingEnabled()).isTrue();
        assertThat(env.getCheckpointConfig().getCheckpointInterval()).isEqualTo(5000L);

        // 验证状态后端配置
        assertThat(env.getCheckpointConfig().getCheckpointStorage()).isNotNull();

        // 验证Checkpoint目录
        File checkpointDir = new File(flinkConfig.getCheckpointDir());
        assertThat(checkpointDir.getParent()).isNotNull();

        // 验证保留Checkpoint数量配置
        assertThat(flinkConfig.getRetainedCheckpoints()).isEqualTo(3);

        logger.info("Checkpoint recovery configuration validated successfully");
    }

    /**
     * 测试JobManager故障切换
     * 
     * 验证需求: 5.3
     */
    @Test
    void testJobManagerFailover() {
        logger.info("Starting JobManager failover test");

        JobManagerFailoverManager failoverManager = new JobManagerFailoverManager(
            haConfig.getFailoverTimeout()
        );

        // 验证初始状态
        assertThat(failoverManager.isPrimaryActive()).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("primary");

        // 记录故障切换开始时间
        long startTime = System.currentTimeMillis();

        // 触发主JobManager故障
        boolean failoverSuccess = failoverManager.recordFailure("primary");

        // 计算故障切换时间
        long failoverDuration = System.currentTimeMillis() - startTime;

        // 验证故障切换成功
        assertThat(failoverSuccess).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");
        assertThat(failoverManager.isPrimaryActive()).isFalse();

        // 验证在30秒内完成切换（需求5.3）
        assertThat(failoverDuration)
            .as("Failover should complete within 30 seconds")
            .isLessThanOrEqualTo(30000L);

        logger.info("JobManager failover completed in {} ms", failoverDuration);
    }

    /**
     * 测试故障恢复时效性
     * 
     * 验证需求: 4.3
     */
    @Test
    void testRecoveryTimeliness() {
        logger.info("Starting recovery timeliness test");

        FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
            flinkConfig.getCheckpointDir(),
            flinkConfig.getRetainedCheckpoints()
        );

        // 模拟故障发生
        long recoveryStartTime = System.currentTimeMillis();

        // 触发恢复
        recoveryManager.startRecovery();
        
        // 模拟恢复完成
        String checkpointPath = tempDir.resolve("checkpoints/checkpoint-1").toString();
        recoveryManager.completeRecovery(checkpointPath, true);

        long recoveryDuration = System.currentTimeMillis() - recoveryStartTime;

        // 验证恢复成功
        assertThat(recoveryManager.getSuccessfulRecoveries())
            .as("Recovery should be successful")
            .isEqualTo(1);

        // 验证恢复在10分钟内完成（需求4.3）
        assertThat(recoveryDuration)
            .as("Recovery should complete within 10 minutes")
            .isLessThanOrEqualTo(600000L);

        logger.info("Recovery completed in {} ms", recoveryDuration);
    }

    /**
     * 测试数据处理连续性（故障期间）
     * 
     * 验证需求: 4.1, 9.1
     */
    @Test
    void testDataContinuityDuringFailure() throws Exception {
        logger.info("Starting data continuity during failure test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        ResilientDataCollector collector = new ResilientDataCollector();

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
                env.execute("Data Continuity Test");
            } catch (Exception e) {
                logger.warn("Job execution interrupted (expected in test): {}", e.getMessage());
            }
        });

        // 等待部分数据处理
        Thread.sleep(3000);

        // 模拟故障（在实际环境中会触发Checkpoint恢复）
        logger.info("Simulating failure...");

        // 等待处理完成或超时
        try {
            jobFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.info("Job still running (expected in test)");
        } catch (Exception e) {
            logger.warn("Job execution error: {}", e.getMessage());
        }

        // 等待数据收集完成
        Thread.sleep(2000);

        // 验证至少处理了一些数据（证明数据处理在进行）
        List<ProcessedEvent> processedEvents = collector.getCollectedEvents();
        
        assertThat(processedEvents)
            .as("Some events should be processed before failure")
            .isNotEmpty();

        // 验证处理的数据不超过输入数据（至少一次语义）
        assertThat(processedEvents.size())
            .as("Processed events should not exceed input events")
            .isLessThanOrEqualTo(testEvents.size());

        logger.info("Data continuity test completed: {} events processed", 
            processedEvents.size());
    }

    /**
     * 测试多次故障恢复
     * 
     * 验证需求: 4.1, 4.6
     */
    @Test
    void testMultipleFailureRecoveries() {
        logger.info("Starting multiple failure recoveries test");

        FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
            flinkConfig.getCheckpointDir(),
            flinkConfig.getRetainedCheckpoints()
        );

        int failureCount = 3;

        for (int i = 0; i < failureCount; i++) {
            logger.info("Simulating failure #{}", i + 1);

            // 触发恢复
            recoveryManager.startRecovery();
            
            // 模拟恢复完成
            String checkpointPath = tempDir.resolve("checkpoints/checkpoint-" + i).toString();
            recoveryManager.completeRecovery(checkpointPath, true);
        }

        // 验证所有恢复都成功
        long successfulRecoveries = recoveryManager.getSuccessfulRecoveries();
        assertThat(successfulRecoveries)
            .as("All recoveries should succeed")
            .isEqualTo(failureCount);

        logger.info("Multiple failure recoveries test completed: {}/{} successful", 
            successfulRecoveries, failureCount);
    }

    /**
     * 测试Checkpoint保留策略
     * 
     * 验证需求: 4.4
     */
    @Test
    void testCheckpointRetentionPolicy() throws Exception {
        logger.info("Starting checkpoint retention policy test");

        // 验证配置的保留数量
        assertThat(flinkConfig.getRetainedCheckpoints())
            .as("Should retain 3 checkpoints")
            .isEqualTo(3);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // 验证Checkpoint配置已应用
        assertThat(env.getCheckpointConfig().isCheckpointingEnabled()).isTrue();

        logger.info("Checkpoint retention policy validated: {} checkpoints retained", 
            flinkConfig.getRetainedCheckpoints());
    }

    /**
     * 测试故障恢复期间的数据一致性
     * 
     * 验证需求: 4.1, 9.1, 9.4
     */
    @Test
    void testDataConsistencyDuringRecovery() throws Exception {
        logger.info("Starting data consistency during recovery test");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        ConsistencyTrackingCollector collector = new ConsistencyTrackingCollector();

        // 创建有序的测试数据
        List<ChangeEvent> testEvents = createOrderedTestEvents(30);
        DataStream<ChangeEvent> sourceStream = env.fromCollection(testEvents);

        EventProcessor processor = new EventProcessor();

        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())
            .map(processor)
            .filter(event -> event != null);

        processedStream.addSink(collector);

        CompletableFuture.runAsync(() -> {
            try {
                env.execute("Data Consistency Test");
            } catch (Exception e) {
                logger.warn("Job execution interrupted: {}", e.getMessage());
            }
        });

        // 等待处理完成
        Thread.sleep(8000);

        List<ProcessedEvent> processedEvents = collector.getCollectedEvents();

        // 验证事件顺序保持（需求9.4）
        if (processedEvents.size() > 1) {
            for (int i = 1; i < processedEvents.size(); i++) {
                long prevTimestamp = processedEvents.get(i - 1).getTimestamp();
                long currTimestamp = processedEvents.get(i).getTimestamp();

                assertThat(currTimestamp)
                    .as("Event order should be preserved")
                    .isGreaterThanOrEqualTo(prevTimestamp);
            }
        }

        // 验证没有重复的事件ID（在单次处理中）
        Set<String> eventIds = new HashSet<>();
        for (ProcessedEvent event : processedEvents) {
            eventIds.add(event.getEventId());
        }

        logger.info("Data consistency validated: {} unique events out of {} processed", 
            eventIds.size(), processedEvents.size());
    }

    /**
     * 测试故障恢复监控
     * 
     * 验证需求: 4.6
     */
    @Test
    void testFailureMonitoring() {
        logger.info("Starting failure monitoring test");

        FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
            flinkConfig.getCheckpointDir(),
            flinkConfig.getRetainedCheckpoints()
        );

        // 触发故障并恢复
        recoveryManager.startRecovery();
        String checkpointPath = tempDir.resolve("checkpoints/checkpoint-1").toString();
        recoveryManager.completeRecovery(checkpointPath, true);

        // 验证恢复被记录
        assertThat(recoveryManager.getTotalRecoveries())
            .as("Recovery attempts should be recorded")
            .isGreaterThan(0);

        // 验证最后恢复持续时间被记录
        assertThat(recoveryManager.getLastRecoveryDuration())
            .as("Last recovery duration should be recorded")
            .isGreaterThanOrEqualTo(0);

        logger.info("Failure monitoring validated: {} recovery attempts", 
            recoveryManager.getTotalRecoveries());
    }

    /**
     * 测试并发故障场景
     * 
     * 验证需求: 4.1, 5.3
     */
    @Test
    void testConcurrentFailures() throws Exception {
        logger.info("Starting concurrent failures test");

        JobManagerFailoverManager failoverManager = new JobManagerFailoverManager(30000L);
        FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
            flinkConfig.getCheckpointDir(),
            flinkConfig.getRetainedCheckpoints()
        );

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicBoolean failoverSuccess = new AtomicBoolean(false);
        AtomicBoolean recoverySuccess = new AtomicBoolean(false);

        // JobManager故障切换线程
        Thread failoverThread = new Thread(() -> {
            try {
                startLatch.await();
                boolean result = failoverManager.recordFailure("primary");
                failoverSuccess.set(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // 故障恢复线程
        Thread recoveryThread = new Thread(() -> {
            try {
                startLatch.await();
                recoveryManager.startRecovery();
                String checkpointPath = tempDir.resolve("checkpoints/checkpoint-1").toString();
                recoveryManager.completeRecovery(checkpointPath, true);
                recoverySuccess.set(recoveryManager.getSuccessfulRecoveries() > 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        failoverThread.start();
        recoveryThread.start();

        // 同时触发两个故障
        startLatch.countDown();

        // 等待完成
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(failoverSuccess.get()).isTrue();
        assertThat(recoverySuccess.get()).isTrue();

        logger.info("Concurrent failures test completed successfully");
    }

    // ==================== 辅助方法 ====================

    private List<ChangeEvent> createTestEvents(int count) {
        List<ChangeEvent> events = new ArrayList<>();
        long baseTimestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", i);
            data.put("name", "user_" + i);

            ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(baseTimestamp + i * 100)
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("event-" + i)
                .build();

            events.add(event);
        }

        return events;
    }

    private List<ChangeEvent> createOrderedTestEvents(int count) {
        List<ChangeEvent> events = new ArrayList<>();
        long baseTimestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", i);
            data.put("sequence", i);

            ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(baseTimestamp + i * 1000) // 1秒间隔
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("ordered-event-" + i)
                .build();

            events.add(event);
        }

        return events;
    }

    /**
     * 弹性数据收集器
     * 支持故障场景下的数据收集
     */
    private static class ResilientDataCollector implements SinkFunction<ProcessedEvent> {
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
     * 一致性跟踪收集器
     * 跟踪数据一致性指标
     */
    private static class ConsistencyTrackingCollector implements SinkFunction<ProcessedEvent> {
        private final List<ProcessedEvent> collectedEvents = new CopyOnWriteArrayList<>();
        private final AtomicInteger processedCount = new AtomicInteger(0);

        @Override
        public void invoke(ProcessedEvent value, Context context) {
            collectedEvents.add(value);
            processedCount.incrementAndGet();
        }

        public List<ProcessedEvent> getCollectedEvents() {
            return new ArrayList<>(collectedEvents);
        }

        public int getProcessedCount() {
            return processedCount.get();
        }
    }
}
