package com.realtime.pipeline.flink;

import com.realtime.pipeline.config.DataHubConfig;
import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.flink.processor.EventProcessor;
import com.realtime.pipeline.flink.source.DataHubSource;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * Flink流处理集成测试
 * 
 * 测试完整的Flink流处理流程：
 * - 数据源消费 (DataHubSource)
 * - 事件处理逻辑 (EventProcessor)
 * - Checkpoint配置 (FlinkEnvironmentConfigurator)
 * 
 * 验证需求: 2.1, 2.3, 2.4
 */
class FlinkStreamProcessingIntegrationTest {

    @TempDir
    Path tempDir;

    private FlinkConfig flinkConfig;
    private DataHubConfig dataHubConfig;

    @BeforeEach
    void setUp() {
        // 创建Flink配置
        flinkConfig = FlinkConfig.builder()
            .parallelism(1)  // 测试环境使用单并行度
            .checkpointInterval(300000L)  // 5分钟
            .checkpointTimeout(600000L)   // 10分钟
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(3)
            .restartStrategy("fixed-delay")
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        // 创建DataHub配置
        dataHubConfig = DataHubConfig.builder()
            .endpoint("http://test-endpoint.com")
            .accessId("test-access-id")
            .accessKey("test-access-key")
            .project("test-project")
            .topic("test-topic")
            .consumerGroup("test-consumer-group")
            .startPosition("LATEST")
            .build();
    }

    @Test
    void testCompleteStreamProcessingPipeline() throws Exception {
        // 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 配置Flink环境
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // 验证环境配置
        assertThat(env.getParallelism()).isEqualTo(1);
        assertThat(env.getCheckpointConfig().isCheckpointingEnabled()).isTrue();
        assertThat(env.getCheckpointConfig().getCheckpointInterval()).isEqualTo(300000L);

        // 创建DataHub Source
        DataHubSource source = new DataHubSource(dataHubConfig);
        
        // 验证Source配置
        assertThat(source.getConsumerGroup()).isEqualTo("test-consumer-group");
        assertThat(source.getStartPosition()).isEqualTo("LATEST");

        // 创建EventProcessor
        EventProcessor processor = new EventProcessor();

        // 验证处理器可以处理事件
        Map<String, Object> testData = new HashMap<>();
        testData.put("id", 1);
        testData.put("name", "test");

        ChangeEvent testEvent = ChangeEvent.builder()
            .eventType("INSERT")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .after(testData)
            .primaryKeys(List.of("id"))
            .eventId("test-event-1")
            .build();

        ProcessedEvent processed = processor.map(testEvent);
        
        assertThat(processed).isNotNull();
        assertThat(processed.getEventType()).isEqualTo("INSERT");
        assertThat(processed.getDatabase()).isEqualTo("testdb");
        assertThat(processed.getTable()).isEqualTo("users");
        assertThat(processed.getData()).isEqualTo(testData);
        assertThat(processed.getEventId()).isEqualTo("test-event-1");
    }

    @Test
    void testDataSourceConsumption() throws Exception {
        // 测试数据源消费功能
        DataHubSource source = new DataHubSource(dataHubConfig);
        
        // 验证Source初始化前的状态
        assertThat(source.isRunning()).isFalse();
        
        // 打开Source
        source.open(new org.apache.flink.configuration.Configuration());
        
        // 验证Source已启动
        assertThat(source.isRunning()).isTrue();
        
        // 关闭Source
        source.close();
        
        // 验证Source已停止
        assertThat(source.isRunning()).isFalse();
    }

    @Test
    void testEventProcessingLogic() throws Exception {
        // 测试事件处理逻辑
        EventProcessor processor = new EventProcessor();
        
        // 测试INSERT事件
        Map<String, Object> insertData = new HashMap<>();
        insertData.put("id", 1);
        insertData.put("name", "张三");
        
        ChangeEvent insertEvent = ChangeEvent.builder()
            .eventType("INSERT")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .after(insertData)
            .primaryKeys(List.of("id"))
            .eventId("insert-1")
            .build();
        
        ProcessedEvent insertResult = processor.map(insertEvent);
        
        assertThat(insertResult).isNotNull();
        assertThat(insertResult.getEventType()).isEqualTo("INSERT");
        assertThat(insertResult.getData()).isEqualTo(insertData);
        assertThat(insertResult.getEventId()).isEqualTo("insert-1");
        
        // 测试UPDATE事件
        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("id", 1);
        beforeData.put("name", "张三");
        
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("id", 1);
        afterData.put("name", "李四");
        
        ChangeEvent updateEvent = ChangeEvent.builder()
            .eventType("UPDATE")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .before(beforeData)
            .after(afterData)
            .primaryKeys(List.of("id"))
            .eventId("update-1")
            .build();
        
        ProcessedEvent updateResult = processor.map(updateEvent);
        
        assertThat(updateResult).isNotNull();
        assertThat(updateResult.getEventType()).isEqualTo("UPDATE");
        assertThat(updateResult.getData()).isEqualTo(afterData);
        assertThat(updateResult.getEventId()).isEqualTo("update-1");
        
        // 测试DELETE事件
        Map<String, Object> deleteData = new HashMap<>();
        deleteData.put("id", 1);
        deleteData.put("name", "张三");
        
        ChangeEvent deleteEvent = ChangeEvent.builder()
            .eventType("DELETE")
            .database("testdb")
            .table("users")
            .timestamp(System.currentTimeMillis())
            .before(deleteData)
            .primaryKeys(List.of("id"))
            .eventId("delete-1")
            .build();
        
        ProcessedEvent deleteResult = processor.map(deleteEvent);
        
        assertThat(deleteResult).isNotNull();
        assertThat(deleteResult.getEventType()).isEqualTo("DELETE");
        assertThat(deleteResult.getData()).isEqualTo(deleteData);
        assertThat(deleteResult.getEventId()).isEqualTo("delete-1");
    }

    @Test
    void testCheckpointConfiguration() {
        // 测试Checkpoint配置
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);
        
        // 验证Checkpoint配置
        assertThat(env.getCheckpointConfig().isCheckpointingEnabled()).isTrue();
        assertThat(env.getCheckpointConfig().getCheckpointInterval()).isEqualTo(300000L);
        assertThat(env.getCheckpointConfig().getCheckpointTimeout()).isEqualTo(600000L);
        assertThat(env.getCheckpointConfig().getMinPauseBetweenCheckpoints()).isEqualTo(60000L);
        assertThat(env.getCheckpointConfig().getMaxConcurrentCheckpoints()).isEqualTo(1);
        assertThat(env.getCheckpointConfig().getTolerableCheckpointFailureNumber()).isEqualTo(3);
        
        // 验证Checkpoint模式（需求9.1：默认至少一次语义）
        assertThat(env.getCheckpointConfig().getCheckpointingMode().name())
            .isEqualTo("AT_LEAST_ONCE");
        
        // 验证外部化Checkpoint配置
        assertThat(env.getCheckpointConfig().getExternalizedCheckpointCleanup())
            .isNotNull();
        
        // 验证状态后端配置
        assertThat(env.getCheckpointConfig().getCheckpointStorage()).isNotNull();
    }

    @Test
    void testEventOrderPreservation() throws Exception {
        // 测试事件顺序保持
        EventProcessor processor = new EventProcessor();
        
        // 创建时间戳递增的事件序列
        long baseTimestamp = System.currentTimeMillis();
        List<ChangeEvent> events = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", i);
            data.put("value", i * 100);
            
            ChangeEvent event = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(baseTimestamp + i * 1000)  // 每个事件间隔1秒
                .after(data)
                .primaryKeys(List.of("id"))
                .eventId("event-" + i)
                .build();
            
            events.add(event);
        }
        
        // 处理所有事件
        List<ProcessedEvent> processedEvents = new ArrayList<>();
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            processedEvents.add(processed);
        }
        
        // 验证事件顺序保持
        for (int i = 1; i < processedEvents.size(); i++) {
            long prevTimestamp = processedEvents.get(i - 1).getTimestamp();
            long currTimestamp = processedEvents.get(i).getTimestamp();
            
            assertThat(currTimestamp)
                .as("Event[%d] timestamp should be >= Event[%d] timestamp", i, i - 1)
                .isGreaterThanOrEqualTo(prevTimestamp);
        }
    }

    @Test
    void testUniqueEventIdGeneration() throws Exception {
        // 测试唯一标识符生成
        EventProcessor processor = new EventProcessor();
        
        // 创建多个没有eventId的事件
        List<ChangeEvent> events = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", i);
            
            // 使用空字符串eventId以触发生成
            ChangeEvent event = new ChangeEvent(
                "INSERT",
                "testdb",
                "users",
                System.currentTimeMillis(),
                null,
                data,
                List.of("id"),
                ""  // 空eventId
            );
            
            events.add(event);
        }
        
        // 处理所有事件
        List<ProcessedEvent> processedEvents = new ArrayList<>();
        for (ChangeEvent event : events) {
            ProcessedEvent processed = processor.map(event);
            processedEvents.add(processed);
        }
        
        // 收集所有eventId
        Set<String> eventIds = new HashSet<>();
        for (ProcessedEvent event : processedEvents) {
            assertThat(event.getEventId())
                .as("Event ID should not be null or empty")
                .isNotNull()
                .isNotEmpty();
            
            eventIds.add(event.getEventId());
        }
        
        // 验证所有eventId都是唯一的
        assertThat(eventIds)
            .as("All event IDs should be unique")
            .hasSize(processedEvents.size());
    }

    @Test
    void testProcessingLatency() throws Exception {
        // 测试处理延迟计算
        EventProcessor processor = new EventProcessor();
        
        // 创建一个过去的事件
        long pastTimestamp = System.currentTimeMillis() - 5000;  // 5秒前
        
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        
        ChangeEvent event = ChangeEvent.builder()
            .eventType("INSERT")
            .database("testdb")
            .table("users")
            .timestamp(pastTimestamp)
            .after(data)
            .primaryKeys(List.of("id"))
            .eventId("test-event")
            .build();
        
        // 处理事件
        ProcessedEvent processed = processor.map(event);
        
        // 验证延迟计算
        assertThat(processed.getProcessingLatency())
            .as("Processing latency should be at least 5000ms")
            .isGreaterThanOrEqualTo(5000L);
        
        // 验证processTime大于原始timestamp
        assertThat(processed.getProcessTime())
            .as("Process time should be greater than original timestamp")
            .isGreaterThan(pastTimestamp);
    }

    @Test
    void testPartitionGeneration() throws Exception {
        // 测试分区生成
        EventProcessor processor = new EventProcessor();
        
        // 创建特定时间的事件
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.of(2025, 1, 28, 15, 30, 0);
        long timestamp = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        
        ChangeEvent event = ChangeEvent.builder()
            .eventType("INSERT")
            .database("testdb")
            .table("users")
            .timestamp(timestamp)
            .after(data)
            .primaryKeys(List.of("id"))
            .eventId("test-event")
            .build();
        
        // 处理事件
        ProcessedEvent processed = processor.map(event);
        
        // 验证分区格式: yyyyMMddHH
        assertThat(processed.getPartition())
            .as("Partition should be in yyyyMMddHH format")
            .isEqualTo("2025012815");
    }

    @Test
    void testNullEventHandling() throws Exception {
        // 测试null事件处理
        EventProcessor processor = new EventProcessor();
        
        ProcessedEvent result = processor.map(null);
        
        assertThat(result).isNull();
    }

    @Test
    void testMultipleEventTypesProcessing() throws Exception {
        // 测试处理多种事件类型
        EventProcessor processor = new EventProcessor();
        
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "test");
        
        long timestamp = System.currentTimeMillis();
        
        // INSERT
        ChangeEvent insertEvent = ChangeEvent.builder()
            .eventType("INSERT")
            .database("testdb")
            .table("users")
            .timestamp(timestamp)
            .after(data)
            .primaryKeys(List.of("id"))
            .eventId("insert-1")
            .build();
        
        ProcessedEvent insertResult = processor.map(insertEvent);
        assertThat(insertResult.getEventType()).isEqualTo("INSERT");
        
        // UPDATE
        ChangeEvent updateEvent = ChangeEvent.builder()
            .eventType("UPDATE")
            .database("testdb")
            .table("users")
            .timestamp(timestamp + 1000)
            .before(data)
            .after(data)
            .primaryKeys(List.of("id"))
            .eventId("update-1")
            .build();
        
        ProcessedEvent updateResult = processor.map(updateEvent);
        assertThat(updateResult.getEventType()).isEqualTo("UPDATE");
        
        // DELETE
        ChangeEvent deleteEvent = ChangeEvent.builder()
            .eventType("DELETE")
            .database("testdb")
            .table("users")
            .timestamp(timestamp + 2000)
            .before(data)
            .primaryKeys(List.of("id"))
            .eventId("delete-1")
            .build();
        
        ProcessedEvent deleteResult = processor.map(deleteEvent);
        assertThat(deleteResult.getEventType()).isEqualTo("DELETE");
        
        // 验证所有事件都被正确处理
        assertThat(insertResult).isNotNull();
        assertThat(updateResult).isNotNull();
        assertThat(deleteResult).isNotNull();
    }

    @Test
    void testConfigurationConsistency() {
        // 测试配置一致性
        StreamExecutionEnvironment env1 = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamExecutionEnvironment env2 = StreamExecutionEnvironment.getExecutionEnvironment();
        
        FlinkEnvironmentConfigurator configurator1 = new FlinkEnvironmentConfigurator(flinkConfig);
        FlinkEnvironmentConfigurator configurator2 = new FlinkEnvironmentConfigurator(flinkConfig);
        
        configurator1.configure(env1);
        configurator2.configure(env2);
        
        // 验证两个环境的配置一致
        assertThat(env1.getParallelism()).isEqualTo(env2.getParallelism());
        assertThat(env1.getCheckpointConfig().getCheckpointInterval())
            .isEqualTo(env2.getCheckpointConfig().getCheckpointInterval());
        assertThat(env1.getCheckpointConfig().getCheckpointingMode())
            .isEqualTo(env2.getCheckpointConfig().getCheckpointingMode());
    }

    @Test
    void testDataSourceConfigurationValidation() {
        // 测试数据源配置验证
        DataHubConfig invalidConfig = DataHubConfig.builder()
            .endpoint("")  // 无效的endpoint
            .accessId("test-access-id")
            .accessKey("test-access-key")
            .project("test-project")
            .topic("test-topic")
            .consumerGroup("test-consumer-group")
            .build();
        
        DataHubSource source = new DataHubSource(invalidConfig);
        
        assertThatThrownBy(() -> source.open(new org.apache.flink.configuration.Configuration()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endpoint");
    }

    @Test
    void testCheckpointIntervalRequirement() {
        // 测试Checkpoint间隔需求（5分钟）
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);
        
        // 验证Checkpoint间隔为5分钟（300000毫秒）
        assertThat(env.getCheckpointConfig().getCheckpointInterval())
            .as("Checkpoint interval should be 5 minutes (300000ms)")
            .isEqualTo(300000L);
    }

    @Test
    void testRetainedCheckpointsRequirement() {
        // 测试保留Checkpoint数量需求（3个）
        assertThat(flinkConfig.getRetainedCheckpoints())
            .as("Should retain 3 checkpoints")
            .isEqualTo(3);
    }
}
