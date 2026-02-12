package com.realtime.pipeline.flink;

import com.realtime.pipeline.config.FlinkConfig;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * FlinkEnvironmentConfigurator的单元测试
 * 验证Flink执行环境配置的正确性
 */
class FlinkEnvironmentConfiguratorTest {

    @TempDir
    Path tempDir;

    private FlinkConfig flinkConfig;
    private StreamExecutionEnvironment env;

    @BeforeEach
    void setUp() {
        // 创建测试配置
        flinkConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L) // 5分钟
            .checkpointTimeout(600000L)  // 10分钟
            .minPauseBetweenCheckpoints(60000L) // 1分钟
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(3)
            .restartStrategy("fixed-delay")
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        // 创建Flink执行环境
        env = StreamExecutionEnvironment.getExecutionEnvironment();
    }

    @Test
    void testConstructorWithNullConfig() {
        // 测试空配置
        assertThatThrownBy(() -> new FlinkEnvironmentConfigurator(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FlinkConfig cannot be null");
    }

    @Test
    void testConstructorWithInvalidConfig() {
        // 测试无效配置
        FlinkConfig invalidConfig = FlinkConfig.builder()
            .parallelism(-1)
            .checkpointInterval(300000L)
            .checkpointDir(tempDir.toString())
            .build();

        assertThatThrownBy(() -> new FlinkEnvironmentConfigurator(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parallelism must be positive");
    }

    @Test
    void testConfigureWithNullEnvironment() {
        // 测试空环境
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        
        assertThatThrownBy(() -> configurator.configure(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("StreamExecutionEnvironment cannot be null");
    }

    @Test
    void testConfigureParallelism() {
        // 测试并行度配置
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        assertThat(env.getParallelism()).isEqualTo(4);
    }

    @Test
    void testConfigureCheckpointing() {
        // 测试Checkpoint配置
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        CheckpointConfig checkpointConfig = env.getCheckpointConfig();

        // 验证Checkpoint间隔（通过检查是否启用了Checkpoint）
        assertThat(checkpointConfig.isCheckpointingEnabled()).isTrue();

        // 验证Checkpoint超时
        assertThat(checkpointConfig.getCheckpointTimeout()).isEqualTo(600000L);

        // 验证最小间隔
        assertThat(checkpointConfig.getMinPauseBetweenCheckpoints()).isEqualTo(60000L);

        // 验证最大并发数
        assertThat(checkpointConfig.getMaxConcurrentCheckpoints()).isEqualTo(1);

        // 验证容忍失败次数
        assertThat(checkpointConfig.getTolerableCheckpointFailureNumber()).isEqualTo(3);

        // 验证外部化Checkpoint已启用
        assertThat(checkpointConfig.getExternalizedCheckpointCleanup())
            .isEqualTo(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
    }

    @Test
    void testConfigureHashMapStateBackend() {
        // 测试HashMapStateBackend配置
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // 验证状态后端已配置（通过检查Checkpoint存储）
        assertThat(env.getCheckpointConfig().getCheckpointStorage()).isNotNull();
    }

    @Test
    void testConfigureRocksDBStateBackend() {
        // 测试RocksDB状态后端配置
        FlinkConfig rocksdbConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("rocksdb")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(3)
            .restartStrategy("fixed-delay")
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(rocksdbConfig);
        configurator.configure(env);

        // 验证状态后端已配置
        assertThat(env.getCheckpointConfig().getCheckpointStorage()).isNotNull();
    }

    @Test
    void testConfigureUnsupportedStateBackend() {
        // 测试不支持的状态后端
        FlinkConfig invalidConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("invalid")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(3)
            .restartStrategy("fixed-delay")
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        // 配置验证应该失败
        assertThatThrownBy(() -> new FlinkEnvironmentConfigurator(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("stateBackendType must be");
    }

    @Test
    void testConfigureFixedDelayRestartStrategy() {
        // 测试固定延迟重启策略
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // 验证重启策略已配置（通过检查环境配置）
        assertThat(env.getRestartStrategy()).isNotNull();
    }

    @Test
    void testConfigureFailureRateRestartStrategy() {
        // 测试失败率重启策略
        FlinkConfig failureRateConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(3)
            .restartStrategy("failure-rate")
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(failureRateConfig);
        configurator.configure(env);

        // 验证重启策略已配置
        assertThat(env.getRestartStrategy()).isNotNull();
    }

    @Test
    void testConfigureNoRestartStrategy() {
        // 测试不重启策略
        FlinkConfig noRestartConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(3)
            .restartStrategy("none")
            .restartAttempts(0)
            .restartDelay(0L)
            .build();

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(noRestartConfig);
        configurator.configure(env);

        // 验证重启策略已配置
        assertThat(env.getRestartStrategy()).isNotNull();
    }

    @Test
    void testConfigureUnsupportedRestartStrategy() {
        // 测试不支持的重启策略
        FlinkConfig invalidConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(3)
            .restartStrategy("invalid")
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        // 配置验证应该失败
        assertThatThrownBy(() -> new FlinkEnvironmentConfigurator(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("restartStrategy must be");
    }

    @Test
    void testGetFlinkConfig() {
        // 测试获取配置
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        
        assertThat(configurator.getFlinkConfig()).isEqualTo(flinkConfig);
    }

    @Test
    void testCompleteConfiguration() {
        // 测试完整配置流程
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        
        // 配置环境
        assertThatCode(() -> configurator.configure(env))
            .doesNotThrowAnyException();

        // 验证所有配置都已应用
        assertThat(env.getParallelism()).isEqualTo(4);
        assertThat(env.getCheckpointConfig().isCheckpointingEnabled()).isTrue();
        assertThat(env.getCheckpointConfig().getCheckpointStorage()).isNotNull();
        assertThat(env.getRestartStrategy()).isNotNull();
    }

    @Test
    void testConfigurationWithMinimalSettings() {
        // 测试最小配置
        FlinkConfig minimalConfig = FlinkConfig.builder()
            .parallelism(1)
            .checkpointInterval(60000L) // 1分钟
            .checkpointTimeout(120000L) // 2分钟
            .minPauseBetweenCheckpoints(10000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(1)
            .stateBackendType("hashmap")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(0)
            .restartStrategy("none")
            .restartAttempts(0)
            .restartDelay(0L)
            .build();

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(minimalConfig);
        
        assertThatCode(() -> configurator.configure(env))
            .doesNotThrowAnyException();
    }

    @Test
    void testConfigurationWithMaximalSettings() {
        // 测试最大配置
        FlinkConfig maximalConfig = FlinkConfig.builder()
            .parallelism(100)
            .checkpointInterval(600000L) // 10分钟
            .checkpointTimeout(1800000L) // 30分钟
            .minPauseBetweenCheckpoints(300000L) // 5分钟
            .maxConcurrentCheckpoints(5)
            .retainedCheckpoints(10)
            .stateBackendType("rocksdb")
            .checkpointDir(tempDir.toString())
            .tolerableCheckpointFailures(10)
            .restartStrategy("failure-rate")
            .restartAttempts(10)
            .restartDelay(60000L)
            .build();

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(maximalConfig);
        
        assertThatCode(() -> configurator.configure(env))
            .doesNotThrowAnyException();
    }

    @Test
    void testGetRecoveryManager() {
        // 测试获取故障恢复管理器
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        
        assertThat(configurator.getRecoveryManager()).isNotNull();
        assertThat(configurator.getRecoveryManager().getRetainedCheckpoints()).isEqualTo(3);
        assertThat(configurator.getRecoveryManager().getCheckpointDir()).contains(tempDir.toString());
    }

    @Test
    void testFaultRecoveryConfiguration() throws Exception {
        // 测试故障恢复配置
        // 创建一些Checkpoint目录
        java.nio.file.Files.createDirectory(tempDir.resolve("checkpoint-1"));
        Thread.sleep(10);
        java.nio.file.Files.createDirectory(tempDir.resolve("checkpoint-2"));
        Thread.sleep(10);
        java.nio.file.Files.createDirectory(tempDir.resolve("checkpoint-3"));
        Thread.sleep(10);
        java.nio.file.Files.createDirectory(tempDir.resolve("checkpoint-4"));

        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // 验证恢复管理器可以找到最新的Checkpoint
        String latestCheckpoint = configurator.getRecoveryManager().getLatestCheckpointPath();
        assertThat(latestCheckpoint).isNotNull();
        assertThat(latestCheckpoint).contains("checkpoint-4");

        // 验证旧的Checkpoint被清理（保留3个）
        assertThat(java.nio.file.Files.exists(tempDir.resolve("checkpoint-1"))).isFalse();
        assertThat(java.nio.file.Files.exists(tempDir.resolve("checkpoint-2"))).isTrue();
        assertThat(java.nio.file.Files.exists(tempDir.resolve("checkpoint-3"))).isTrue();
        assertThat(java.nio.file.Files.exists(tempDir.resolve("checkpoint-4"))).isTrue();
    }

    @Test
    void testRecoveryManagerMetrics() {
        // 测试恢复管理器指标
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        
        assertThat(configurator.getRecoveryManager().getTotalRecoveries()).isEqualTo(0);
        assertThat(configurator.getRecoveryManager().getSuccessfulRecoveries()).isEqualTo(0);
        assertThat(configurator.getRecoveryManager().getFailedRecoveries()).isEqualTo(0);
        assertThat(configurator.getRecoveryManager().getRecoveryTimeThreshold()).isEqualTo(600000L);
    }
}
