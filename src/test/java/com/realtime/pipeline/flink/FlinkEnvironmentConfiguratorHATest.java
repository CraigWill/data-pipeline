package com.realtime.pipeline.flink;

import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.config.HighAvailabilityConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Flink环境配置器高可用性测试
 * 
 * 验证需求: 5.1, 5.2, 5.3
 */
class FlinkEnvironmentConfiguratorHATest {

    @TempDir
    Path tempDir;

    @Test
    void testConfigureWithZooKeeperHA() {
        // 需求 5.1: THE System SHALL 支持Flink JobManager的高可用配置
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir(tempDir.resolve("ha").toString())
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .build();

        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 应用配置
        assertThatCode(() -> configurator.configure(env))
            .doesNotThrowAnyException();

        // 验证HA已启用
        assertThat(configurator.isHAEnabled()).isTrue();
        assertThat(configurator.getHaConfigurator()).isNotNull();
        assertThat(configurator.getHaConfigurator().getJobManagerCount()).isEqualTo(2);
    }

    @Test
    void testConfigureWithKubernetesHA() {
        // 需求 5.1: THE System SHALL 支持Flink JobManager的高可用配置
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("kubernetes")
            .kubernetesNamespace("flink")
            .kubernetesClusterId("test-cluster")
            .storageDir(tempDir.resolve("ha").toString())
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .build();

        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 应用配置
        assertThatCode(() -> configurator.configure(env))
            .doesNotThrowAnyException();

        // 验证HA已启用
        assertThat(configurator.isHAEnabled()).isTrue();
    }

    @Test
    void testConfigureWithoutHA() {
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        // 不提供HA配置
        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 应用配置
        assertThatCode(() -> configurator.configure(env))
            .doesNotThrowAnyException();

        // 验证HA未启用
        assertThat(configurator.isHAEnabled()).isFalse();
        assertThat(configurator.getHaConfigurator()).isNull();
    }

    @Test
    void testConfigureWithDisabledHA() {
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(false)
            .mode("none")
            .build();

        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 应用配置
        assertThatCode(() -> configurator.configure(env))
            .doesNotThrowAnyException();

        // 验证HA未启用
        assertThat(configurator.isHAEnabled()).isFalse();
    }

    @Test
    void testJobManagerCountRequirement() {
        // 需求 5.2: THE System SHALL 支持至少2个JobManager实例
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir(tempDir.resolve("ha").toString())
            .jobManagerCount(3)  // 3个JobManager
            .build();

        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

        assertThat(configurator.getHaConfigurator().getJobManagerCount())
            .isGreaterThanOrEqualTo(2);
    }

    @Test
    void testFailoverTimeoutRequirement() {
        // 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir(tempDir.resolve("ha").toString())
            .jobManagerCount(2)
            .failoverTimeout(30000L)  // 30秒
            .build();

        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

        assertThat(configurator.getHaConfigurator().getFailoverTimeout())
            .isLessThanOrEqualTo(30000L);
    }

    @Test
    void testGetHaConfigurator() {
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir(tempDir.resolve("ha").toString())
            .jobManagerCount(2)
            .build();

        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

        assertThat(configurator.getHaConfigurator()).isNotNull();
        assertThat(configurator.getHaConfigurator().getHaConfig()).isEqualTo(haConfig);
    }

    @Test
    void testIsHAEnabled() {
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        // 测试启用HA
        HighAvailabilityConfig enabledHaConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir(tempDir.resolve("ha").toString())
            .jobManagerCount(2)
            .build();

        FlinkEnvironmentConfigurator enabledConfigurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, enabledHaConfig);
        assertThat(enabledConfigurator.isHAEnabled()).isTrue();

        // 测试禁用HA
        FlinkEnvironmentConfigurator disabledConfigurator = 
            new FlinkEnvironmentConfigurator(flinkConfig);
        assertThat(disabledConfigurator.isHAEnabled()).isFalse();
    }
}
