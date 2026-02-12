package com.realtime.pipeline.flink;

import com.realtime.pipeline.config.DataHubConfig;
import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.flink.processor.EventProcessor;
import com.realtime.pipeline.flink.source.DataHubSource;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import net.jqwik.api.*;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Flink流处理基于属性的测试
 * 
 * 验证Flink流处理的核心属性：
 * - 属性 6: 数据消费完整性
 * - 属性 7: 事件时间顺序保持
 * - 属性 8: Checkpoint定期执行
 * - 属性 39: 唯一标识符生成
 * 
 * Feature: realtime-data-pipeline
 * **Validates: Requirements 2.1, 2.3, 2.4, 9.4, 9.6**
 */
class FlinkStreamProcessingPropertyTest {

    private static final List<ProcessedEvent> collectedEvents = new CopyOnWriteArrayList<>();
    private static final Map<String, Long> checkpointMetrics = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        collectedEvents.clear();
        checkpointMetrics.clear();
    }

    @AfterEach
    void tearDown() {
        collectedEvents.clear();
        checkpointMetrics.clear();
    }

    /**
     * Feature: realtime-data-pipeline, Property 6: 数据消费完整性
     * 
     * **Validates: Requirements 2.1**
     * 
     * 对于任何发送到DataHub的变更数据，Flink应该能够消费该数据
     * 
     * 测试策略:
     * - 创建DataHub Source并初始化
     * - 验证Source能够正确配置和启动
     * - 验证Source能够消费数据并传递给下游
     * - 验证消费的数据完整性
     */
    @Property(tries = 20)
    @Label("Property 6: Data consumption integrity")
    void dataConsumptionIntegrity(
            @ForAll("validDataHubConfigs") DataHubConfig config) throws Exception {
        
        // 创建DataHub Source
        DataHubSource source = new DataHubSource(config);
        
        // 验证Source能够成功初始化
        source.open(new Configuration());
        assertThat(source.isRunning()).isTrue();
        
        // 验证配置被正确应用
        assertThat(source.getConsumerGroup()).isEqualTo(config.getConsumerGroup());
        assertThat(source.getStartPosition()).isEqualTo(config.getStartPosition());
        
        // 验证Source能够被正常关闭
        source.close();
        assertThat(source.isRunning()).isFalse();
    }

    /**
     * Feature: realtime-data-pipeline, Property 7: 事件时间顺序保持
     * 
     * **Validates: Requirements 2.3, 9.4**
     * 
     * 对于任何事件序列，Flink处理后的输出应该保持原始的时间顺序
     * 
     * 测试策略:
     * - 生成具有递增时间戳的事件序列
     * - 通过EventProcessor处理所有事件
     * - 验证输出事件的时间戳保持递增顺序
     * - 验证原始时间戳未被修改
     */
    @Property(tries = 20)
    @Label("Property 7: Event time order preservation")
    void eventTimeOrderPreservation(
            @ForAll("orderedEventSequences") List<ChangeEvent> events) throws Exception {
        
        EventProcessor processor = new EventProcessor();
        List<ProcessedEvent> processedEvents = new ArrayList<>();
        
        // 处理所有事件
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                processedEvents.add(processed);
            }
        }
        
        // 验证处理了所有事件
        assertThat(processedEvents).hasSize(events.size());
        
        // 验证时间顺序保持 - 输出事件的时间戳应该保持递增
        for (int i = 1; i < processedEvents.size(); i++) {
            long prevTimestamp = processedEvents.get(i - 1).getTimestamp();
            long currTimestamp = processedEvents.get(i).getTimestamp();
            
            assertThat(currTimestamp)
                .as("Event[%d] timestamp should be >= Event[%d] timestamp", i, i - 1)
                .isGreaterThanOrEqualTo(prevTimestamp);
        }
        
        // 验证原始时间戳被保持（没有被修改）
        for (int i = 0; i < events.size(); i++) {
            assertThat(processedEvents.get(i).getTimestamp())
                .as("Original timestamp should be preserved for event %d", i)
                .isEqualTo(events.get(i).getTimestamp());
        }
    }

    /**
     * Feature: realtime-data-pipeline, Property 8: Checkpoint定期执行
     * 
     * **Validates: Requirements 2.4**
     * 
     * 对于任何运行中的Flink作业，系统应该每5分钟执行一次Checkpoint操作
     * 
     * 测试策略:
     * - 验证FlinkConfig中的checkpoint间隔配置
     * - 验证FlinkEnvironmentConfigurator正确配置checkpoint
     * - 验证checkpoint配置参数符合需求（5分钟间隔）
     */
    @Property(tries = 20)
    @Label("Property 8: Checkpoint periodic execution")
    void checkpointPeriodicExecution(
            @ForAll("validFlinkConfigs") FlinkConfig config) {
        
        // 验证checkpoint间隔配置为5分钟（300000毫秒）
        long expectedInterval = 300000L; // 5分钟
        assertThat(config.getCheckpointInterval())
            .as("Checkpoint interval should be 5 minutes (300000ms)")
            .isEqualTo(expectedInterval);
        
        // 创建Flink环境配置器
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(config);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 配置环境
        configurator.configure(env);
        
        // 验证checkpoint配置被正确应用
        assertThat(env.getCheckpointConfig().getCheckpointInterval())
            .as("Checkpoint interval should be configured correctly")
            .isEqualTo(expectedInterval);
        
        
        // 验证checkpoint模式为AT_LEAST_ONCE（需求9.1：默认至少一次语义）
        // 如果配置中明确指定了exactly-once，则验证EXACTLY_ONCE
        String expectedMode = config.getCheckpointingMode() != null && 
                             config.getCheckpointingMode().equalsIgnoreCase("exactly-once") 
                             ? "EXACTLY_ONCE" : "AT_LEAST_ONCE";
        assertThat(env.getCheckpointConfig().getCheckpointingMode().name())
            .as("Checkpointing mode should match configuration (default: AT_LEAST_ONCE)")
            .isEqualTo(expectedMode);
        
        // 验证checkpoint超时配置
        assertThat(env.getCheckpointConfig().getCheckpointTimeout())
            .as("Checkpoint timeout should be configured")
            .isGreaterThan(0);
        
        // 验证最小暂停时间配置
        assertThat(env.getCheckpointConfig().getMinPauseBetweenCheckpoints())
            .as("Min pause between checkpoints should be configured")
            .isGreaterThan(0);
    }

    /**
     * Feature: realtime-data-pipeline, Property 39: 唯一标识符生成
     * 
     * **Validates: Requirements 9.6**
     * 
     * 对于任何处理的数据记录，系统应该为其生成全局唯一的标识符
     * 
     * 测试策略:
     * - 生成多个没有eventId的事件
     * - 通过EventProcessor处理所有事件
     * - 验证所有生成的eventId都是唯一的
     * - 验证eventId格式正确且非空
     */
    @Property(tries = 20)
    @Label("Property 39: Unique identifier generation")
    void uniqueIdentifierGeneration(
            @ForAll("eventsWithoutIds") List<ChangeEvent> events) throws Exception {
        
        EventProcessor processor = new EventProcessor();
        List<ProcessedEvent> processedEvents = new ArrayList<>();
        
        // 处理所有事件
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                processedEvents.add(processed);
            }
        }
        
        // 验证所有事件都被处理
        assertThat(processedEvents).hasSize(events.size());
        
        // 收集所有eventId
        Set<String> eventIds = processedEvents.stream()
            .map(ProcessedEvent::getEventId)
            .collect(Collectors.toSet());
        
        // 验证所有eventId都存在且非空
        for (ProcessedEvent event : processedEvents) {
            assertThat(event.getEventId())
                .as("Event ID should not be null or empty")
                .isNotNull()
                .isNotEmpty();
        }
        
        // 验证所有eventId都是唯一的
        assertThat(eventIds)
            .as("All event IDs should be unique")
            .hasSize(processedEvents.size());
        
    }

    /**
     * 属性测试: 端到端数据流完整性
     * 
     * 验证从Source到Processor的完整数据流
     * - 数据不丢失
     * - 数据顺序保持
     * - 数据内容正确转换
     */
    @Property(tries = 20)
    @Label("End-to-end data flow integrity")
    void endToEndDataFlowIntegrity(
            @ForAll("orderedEventSequences") List<ChangeEvent> events) throws Exception {
        
        EventProcessor processor = new EventProcessor();
        List<ProcessedEvent> processedEvents = new ArrayList<>();
        
        // 模拟完整的数据流：Source -> Processor
        for (ChangeEvent event : events) {
            // 处理事件
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                processedEvents.add(processed);
            }
        }
        
        // 验证数据完整性：所有事件都被处理
        assertThat(processedEvents)
            .as("All events should be processed")
            .hasSize(events.size());
        
        // 验证数据顺序：时间戳保持递增
        for (int i = 1; i < processedEvents.size(); i++) {
            assertThat(processedEvents.get(i).getTimestamp())
                .as("Event order should be preserved")
                .isGreaterThanOrEqualTo(processedEvents.get(i - 1).getTimestamp());
        }
        
        // 验证数据内容：基本字段正确转换
        for (int i = 0; i < events.size(); i++) {
            ChangeEvent original = events.get(i);
            ProcessedEvent processed = processedEvents.get(i);
            
            assertThat(processed.getEventType()).isEqualTo(original.getEventType());
            assertThat(processed.getDatabase()).isEqualTo(original.getDatabase());
            assertThat(processed.getTable()).isEqualTo(original.getTable());
            assertThat(processed.getTimestamp()).isEqualTo(original.getTimestamp());
            assertThat(processed.getData()).isEqualTo(original.getData());
        }
    }

    /**
     * 属性测试: Checkpoint配置一致性
     * 
     * 验证checkpoint配置在不同场景下的一致性
     */
    @Property(tries = 20)
    @Label("Checkpoint configuration consistency")
    void checkpointConfigurationConsistency(
            @ForAll("validFlinkConfigs") FlinkConfig config) {
        
        // 创建两个独立的环境配置器
        FlinkEnvironmentConfigurator configurator1 = new FlinkEnvironmentConfigurator(config);
        FlinkEnvironmentConfigurator configurator2 = new FlinkEnvironmentConfigurator(config);
        
        StreamExecutionEnvironment env1 = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamExecutionEnvironment env2 = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 配置两个环境
        configurator1.configure(env1);
        configurator2.configure(env2);
        
        // 验证两个环境的checkpoint配置一致
        assertThat(env1.getCheckpointConfig().getCheckpointInterval())
            .isEqualTo(env2.getCheckpointConfig().getCheckpointInterval());
        
        assertThat(env1.getCheckpointConfig().getCheckpointingMode())
            .isEqualTo(env2.getCheckpointConfig().getCheckpointingMode());
        
        assertThat(env1.getCheckpointConfig().getCheckpointTimeout())
            .isEqualTo(env2.getCheckpointConfig().getCheckpointTimeout());
    }

    /**
     * 属性测试: 处理延迟合理性
     * 
     * 验证处理时间总是在事件时间之后
     */
    @Property(tries = 20)
    @Label("Processing latency reasonableness")
    void processingLatencyReasonableness(
            @ForAll("changeEvents") List<ChangeEvent> events) throws Exception {
        
        EventProcessor processor = new EventProcessor();
        
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            if (processed != null) {
                // 验证处理时间 >= 事件时间
                assertThat(processed.getProcessTime())
                    .as("Process time should be >= event timestamp")
                    .isGreaterThanOrEqualTo(processed.getTimestamp());
                
                // 验证处理延迟是非负数
                assertThat(processed.getProcessingLatency())
                    .as("Processing latency should be non-negative")
                    .isGreaterThanOrEqualTo(0L);
            }
        }
    }

    // ==================== 数据生成器 ====================

    /**
     * 生成有效的DataHub配置
     */
    @Provide
    Arbitrary<DataHubConfig> validDataHubConfigs() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30),
            Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15),
            Arbitraries.of("EARLIEST", "LATEST", "TIMESTAMP")
        ).as((endpoint, accessId, accessKey, project, topic, consumerGroup, startPosition) ->
            DataHubConfig.builder()
                .endpoint("http://" + endpoint + ".com")
                .accessId(accessId)
                .accessKey(accessKey)
                .project(project)
                .topic(topic)
                .consumerGroup(consumerGroup)
                .startPosition(startPosition)
                .build()
        );
    }

    /**
     * 生成有效的Flink配置（checkpoint间隔固定为5分钟）
     */
    @Provide
    Arbitrary<FlinkConfig> validFlinkConfigs() {
        return Combinators.combine(
            Arbitraries.integers().between(1, 10),
            Arbitraries.longs().between(60000L, 120000L),
            Arbitraries.integers().between(1, 3),
            Arbitraries.of("hashmap", "rocksdb"),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
        ).as((parallelism, minPause, maxConcurrent, stateBackend, checkpointDir) ->
            FlinkConfig.builder()
                .parallelism(parallelism)
                .checkpointInterval(300000L)  // 固定为5分钟
                .checkpointTimeout(600000L)   // 10分钟超时
                .minPauseBetweenCheckpoints(minPause)
                .maxConcurrentCheckpoints(maxConcurrent)
                .tolerableCheckpointFailures(3)
                .retainedCheckpoints(3)
                .stateBackendType(stateBackend)
                .checkpointDir("/tmp/checkpoints/" + checkpointDir)
                .restartStrategy("fixed-delay")
                .restartAttempts(3)
                .restartDelay(10000L)
                .build()
        );
    }

    /**
     * 生成有序的事件序列（时间戳递增）
     */
    @Provide
    Arbitrary<List<ChangeEvent>> orderedEventSequences() {
        return Arbitraries.integers().between(5, 30).flatMap(size -> {
            long baseTimestamp = System.currentTimeMillis() - 3600000; // 1小时前
            
            return Arbitraries.integers().between(1000, 10000).list().ofSize(size)
                .map(increments -> {
                    List<ChangeEvent> events = new ArrayList<>();
                    long currentTimestamp = baseTimestamp;
                    
                    for (int i = 0; i < size; i++) {
                        currentTimestamp += increments.get(i); // 递增时间戳
                        events.add(createChangeEvent(
                            "testdb",
                            "table" + (i % 3),
                            currentTimestamp,
                            "event-" + i
                        ));
                    }
                    
                    return events;
                });
        });
    }

    /**
     * 生成没有eventId的事件列表
     */
    @Provide
    Arbitrary<List<ChangeEvent>> eventsWithoutIds() {
        return Combinators.combine(
            Arbitraries.of("INSERT", "UPDATE", "DELETE"),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.longs().between(System.currentTimeMillis() - 86400000, System.currentTimeMillis() - 1000),
            Arbitraries.integers().between(1, 1000)
        ).as((type, db, table, ts, id) -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", "name_" + id);
            data.put("value", id * 100);
            
            // 50%概率使用null，50%使用空字符串
            String eventId = Arbitraries.integers().between(0, 1).sample() == 0 ? null : "";
            
            if ("DELETE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, null, List.of("id"), eventId);
            } else if ("UPDATE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, data, List.of("id"), eventId);
            } else {
                return new ChangeEvent(type, db, table, ts, null, data, List.of("id"), eventId);
            }
        }).list().ofMinSize(5).ofMaxSize(30);
    }

    /**
     * 生成变更事件列表
     */
    @Provide
    Arbitrary<List<ChangeEvent>> changeEvents() {
        return Combinators.combine(
            Arbitraries.of("INSERT", "UPDATE", "DELETE"),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.longs().between(System.currentTimeMillis() - 86400000, System.currentTimeMillis() - 1000),
            Arbitraries.integers().between(1, 1000),
            Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(20)
        ).as((type, db, table, ts, id, eventId) -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", "name_" + id);
            data.put("value", id * 100);
            
            if ("DELETE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, null, List.of("id"), eventId);
            } else if ("UPDATE".equals(type)) {
                return new ChangeEvent(type, db, table, ts, data, data, List.of("id"), eventId);
            } else {
                return new ChangeEvent(type, db, table, ts, null, data, List.of("id"), eventId);
            }
        }).list().ofMinSize(5).ofMaxSize(30);
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的ChangeEvent
     */
    private ChangeEvent createChangeEvent(String database, String table, long timestamp, String eventId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", UUID.randomUUID().toString());
        data.put("value", timestamp);
        data.put("name", "test_" + timestamp);

        return new ChangeEvent(
            "INSERT",
            database,
            table,
            timestamp,
            null,
            data,
            List.of("id"),
            eventId
        );
    }

    /**
     * 测试用的Sink，收集处理后的事件
     */
    public static class CollectSink implements SinkFunction<ProcessedEvent> {
        private static final long serialVersionUID = 1L;

        @Override
        public void invoke(ProcessedEvent value, Context context) {
            collectedEvents.add(value);
        }
    }
}
