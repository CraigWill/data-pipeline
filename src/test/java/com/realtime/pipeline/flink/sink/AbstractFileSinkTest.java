package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.RollingPolicy;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * AbstractFileSink单元测试
 * 测试文件滚动策略、文件命名策略和重试机制
 */
class AbstractFileSinkTest {

    private OutputConfig config;

    @BeforeEach
    void setUp() {
        config = OutputConfig.builder()
                .path("/tmp/test-output")
                .format("json")
                .rollingSizeBytes(1024L * 1024L * 1024L) // 1GB
                .rollingIntervalMs(3600000L) // 1 hour
                .compression("none")
                .maxRetries(3)
                .retryBackoff(2)
                .build();
    }

    @Test
    void testConstructorWithValidConfig() {
        // 测试使用有效配置创建实例
        TestFileSink sink = new TestFileSink(config);
        assertThat(sink).isNotNull();
        assertThat(sink.config).isEqualTo(config);
    }

    @Test
    void testConstructorWithNullConfig() {
        // 测试使用null配置应该抛出异常
        assertThatThrownBy(() -> new TestFileSink(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OutputConfig cannot be null");
    }

    @Test
    void testConstructorWithInvalidConfig() {
        // 测试使用无效配置应该抛出异常
        OutputConfig invalidConfig = OutputConfig.builder()
                .path("") // 空路径
                .format("json")
                .build();

        assertThatThrownBy(() -> new TestFileSink(invalidConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Output path is required");
    }

    @Test
    void testCreateRollingPolicy() {
        // 测试创建滚动策略
        TestFileSink sink = new TestFileSink(config);
        RollingPolicy<ProcessedEvent, String> policy = sink.createRollingPolicy();
        
        assertThat(policy).isNotNull();
        // 滚动策略应该配置了大小和时间阈值
    }

    @Test
    void testCreateBucketAssigner() {
        // 测试创建桶分配器
        TestFileSink sink = new TestFileSink(config);
        BucketAssigner<ProcessedEvent, String> assigner = sink.createBucketAssigner();
        
        assertThat(assigner).isNotNull();
        assertThat(assigner).isInstanceOf(AbstractFileSink.TimestampPartitionBucketAssigner.class);
    }

    @Test
    void testCreateOutputFileConfig() {
        // 测试创建输出文件配置
        TestFileSink sink = new TestFileSink(config);
        OutputFileConfig fileConfig = sink.createOutputFileConfig("json");
        
        assertThat(fileConfig).isNotNull();
        assertThat(fileConfig.getPartPrefix()).isEqualTo("part");
        assertThat(fileConfig.getPartSuffix()).isEqualTo(".json");
    }

    @Test
    void testGetOutputPath() {
        // 测试获取输出路径
        TestFileSink sink = new TestFileSink(config);
        assertThat(sink.getOutputPath().getPath()).isEqualTo("/tmp/test-output");
    }

    @Test
    void testTimestampPartitionBucketAssigner() {
        // 测试时间戳分区桶分配器
        AbstractFileSink.TimestampPartitionBucketAssigner assigner = 
                new AbstractFileSink.TimestampPartitionBucketAssigner();

        ProcessedEvent event = createTestEvent("mydb", "users", 1706428800000L); // 2024-01-28 12:00:00
        
        String bucketId = assigner.getBucketId(event, null);
        
        // 验证桶ID格式: database/table/dt=yyyyMMddHH
        assertThat(bucketId).matches("mydb/users/dt=\\d{10}");
        assertThat(bucketId).contains("mydb");
        assertThat(bucketId).contains("users");
        assertThat(bucketId).contains("dt=");
    }

    @Test
    void testTimestampPartitionBucketAssignerWithSpecialCharacters() {
        // 测试桶分配器处理特殊字符
        AbstractFileSink.TimestampPartitionBucketAssigner assigner = 
                new AbstractFileSink.TimestampPartitionBucketAssigner();

        ProcessedEvent event = createTestEvent("my/db", "user:table", 1706428800000L);
        
        String bucketId = assigner.getBucketId(event, null);
        
        // 特殊字符应该被替换为下划线（但路径分隔符/是正常的）
        assertThat(bucketId).doesNotContain(":");
        assertThat(bucketId).contains("my_db");
        assertThat(bucketId).contains("user_table");
        // 验证路径结构正确
        assertThat(bucketId).matches("my_db/user_table/dt=\\d{10}");
    }

    @Test
    void testTimestampPartitionBucketAssignerWithNullValues() {
        // 测试桶分配器处理null值
        AbstractFileSink.TimestampPartitionBucketAssigner assigner = 
                new AbstractFileSink.TimestampPartitionBucketAssigner();

        ProcessedEvent event = createTestEvent(null, null, 1706428800000L);
        
        String bucketId = assigner.getBucketId(event, null);
        
        // null值应该被替换为"unknown"
        assertThat(bucketId).contains("unknown");
    }

    @Test
    void testExecuteWithRetrySuccess() throws Exception {
        // 测试重试机制 - 成功场景
        TestFileSink sink = new TestFileSink(config);
        
        final int[] executionCount = {0};
        Runnable operation = () -> executionCount[0]++;
        
        sink.executeWithRetry(operation, "test operation");
        
        // 操作应该执行一次
        assertThat(executionCount[0]).isEqualTo(1);
    }

    @Test
    void testExecuteWithRetryFailure() {
        // 测试重试机制 - 失败场景
        TestFileSink sink = new TestFileSink(config);
        
        final int[] executionCount = {0};
        Runnable operation = () -> {
            executionCount[0]++;
            throw new RuntimeException("Test failure");
        };
        
        // 应该抛出异常（所有重试都失败）
        assertThatThrownBy(() -> sink.executeWithRetry(operation, "test operation"))
                .isInstanceOf(Exception.class);
        
        // 应该重试了maxRetries次
        assertThat(executionCount[0]).isEqualTo(config.getMaxRetries());
    }

    @Test
    void testExecuteWithRetryPartialFailure() throws Exception {
        // 测试重试机制 - 部分失败场景
        TestFileSink sink = new TestFileSink(config);
        
        final int[] executionCount = {0};
        Runnable operation = () -> {
            executionCount[0]++;
            if (executionCount[0] < 2) {
                throw new RuntimeException("Test failure");
            }
            // 第二次成功
        };
        
        sink.executeWithRetry(operation, "test operation");
        
        // 应该执行了2次（第一次失败，第二次成功）
        assertThat(executionCount[0]).isEqualTo(2);
    }

    @Test
    void testDifferentRollingSizes() {
        // 测试不同的滚动大小配置
        long[] sizes = {
                100L * 1024L * 1024L,      // 100MB
                500L * 1024L * 1024L,      // 500MB
                1024L * 1024L * 1024L,     // 1GB
                2048L * 1024L * 1024L      // 2GB
        };

        for (long size : sizes) {
            OutputConfig testConfig = OutputConfig.builder()
                    .path("/tmp/test")
                    .format("json")
                    .rollingSizeBytes(size)
                    .rollingIntervalMs(3600000L)
                    .build();

            TestFileSink sink = new TestFileSink(testConfig);
            RollingPolicy<ProcessedEvent, String> policy = sink.createRollingPolicy();
            
            assertThat(policy).isNotNull();
        }
    }

    @Test
    void testDifferentRollingIntervals() {
        // 测试不同的滚动时间间隔配置
        long[] intervals = {
                60000L,        // 1 minute
                300000L,       // 5 minutes
                3600000L,      // 1 hour
                7200000L       // 2 hours
        };

        for (long interval : intervals) {
            OutputConfig testConfig = OutputConfig.builder()
                    .path("/tmp/test")
                    .format("json")
                    .rollingSizeBytes(1024L * 1024L * 1024L)
                    .rollingIntervalMs(interval)
                    .build();

            TestFileSink sink = new TestFileSink(testConfig);
            RollingPolicy<ProcessedEvent, String> policy = sink.createRollingPolicy();
            
            assertThat(policy).isNotNull();
        }
    }

    @Test
    void testCompressionConfiguration() {
        // 测试不同的压缩配置
        // 验证需求 3.1, 3.2, 3.3: 输出格式支持压缩
        String[] compressions = {"none", "gzip", "snappy"};

        for (String compression : compressions) {
            OutputConfig testConfig = OutputConfig.builder()
                    .path("/tmp/test")
                    .format("json")
                    .rollingSizeBytes(1024L * 1024L * 1024L)
                    .rollingIntervalMs(3600000L)
                    .compression(compression)
                    .build();

            TestFileSink sink = new TestFileSink(testConfig);
            assertThat(sink.config.getCompression()).isEqualTo(compression);
        }
    }

    @Test
    void testRetryBackoffConfiguration() {
        // 测试重试退避时间配置
        // 验证需求 3.7: 写入失败重试配置
        int[] backoffs = {1, 2, 5, 10};

        for (int backoff : backoffs) {
            OutputConfig testConfig = OutputConfig.builder()
                    .path("/tmp/test")
                    .format("json")
                    .rollingSizeBytes(1024L * 1024L * 1024L)
                    .rollingIntervalMs(3600000L)
                    .maxRetries(3)
                    .retryBackoff(backoff)
                    .build();

            TestFileSink sink = new TestFileSink(testConfig);
            assertThat(sink.config.getRetryBackoff()).isEqualTo(backoff);
        }
    }

    // 辅助方法：创建测试事件
    private ProcessedEvent createTestEvent(String database, String table, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "test");

        return ProcessedEvent.builder()
                .eventType("INSERT")
                .database(database)
                .table(table)
                .timestamp(timestamp)
                .processTime(System.currentTimeMillis())
                .data(data)
                .partition("0")
                .eventId("test-event-1")
                .build();
    }

    // 测试用的具体实现类
    private static class TestFileSink extends AbstractFileSink {
        private static final long serialVersionUID = 1L;

        public TestFileSink(OutputConfig config) {
            super(config);
        }

        @Override
        public StreamingFileSink<ProcessedEvent> createSink() {
            // 简单实现用于测试
            return null;
        }
    }
}
