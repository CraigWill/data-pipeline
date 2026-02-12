package com.realtime.pipeline.flink.ha;

import com.realtime.pipeline.config.HighAvailabilityConfig;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.HighAvailabilityOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 高可用性配置器单元测试
 * 
 * 验证需求: 5.1, 5.2, 5.3
 */
class HighAvailabilityConfiguratorTest {

    @Test
    void testZooKeeperHAConfiguration() {
        // 需求 5.1: THE System SHALL 支持Flink JobManager的高可用配置
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181,localhost:2182")
            .zookeeperPath("/flink-test")
            .storageDir("/tmp/flink-ha-test")
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .zookeeperSessionTimeout(60000L)
            .zookeeperConnectionTimeout(15000L)
            .build();

        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);
        Configuration config = new Configuration();

        // 应用配置
        configurator.configure(config);

        // 验证ZooKeeper配置
        assertThat(config.getString(HighAvailabilityOptions.HA_MODE)).isEqualTo("zookeeper");
        assertThat(config.getString(HighAvailabilityOptions.HA_ZOOKEEPER_QUORUM))
            .isEqualTo("localhost:2181,localhost:2182");
        assertThat(config.getString(HighAvailabilityOptions.HA_ZOOKEEPER_ROOT))
            .isEqualTo("/flink-test");
        assertThat(config.getString(HighAvailabilityOptions.HA_STORAGE_PATH))
            .contains("/tmp/flink-ha-test");
    }

    @Test
    void testKubernetesHAConfiguration() {
        // 需求 5.1: THE System SHALL 支持Flink JobManager的高可用配置
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("kubernetes")
            .kubernetesNamespace("flink-test")
            .kubernetesClusterId("test-cluster")
            .storageDir("/opt/flink/ha-test")
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .build();

        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);
        Configuration config = new Configuration();

        // 应用配置
        configurator.configure(config);

        // 验证Kubernetes配置
        assertThat(config.getString(HighAvailabilityOptions.HA_MODE)).isEqualTo("kubernetes");
        assertThat(config.toMap().get("kubernetes.namespace")).isEqualTo("flink-test");
        assertThat(config.toMap().get("kubernetes.cluster-id")).isEqualTo("test-cluster");
        assertThat(config.getString(HighAvailabilityOptions.HA_STORAGE_PATH))
            .contains("/opt/flink/ha-test");
    }

    @Test
    void testDisabledHA() {
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(false)
            .mode("none")
            .build();

        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);
        Configuration config = new Configuration();

        // 应用配置
        configurator.configure(config);

        // 验证HA被禁用
        assertThat(config.getString(HighAvailabilityOptions.HA_MODE)).isEqualTo("NONE");
        assertThat(configurator.isHAEnabled()).isFalse();
    }

    @Test
    void testStorageDirectoryWithoutScheme() {
        // 测试存储目录自动添加file://前缀
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")  // 没有scheme
            .jobManagerCount(2)
            .build();

        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);
        Configuration config = new Configuration();

        configurator.configure(config);

        // 验证自动添加了file://前缀
        String storagePath = config.getString(HighAvailabilityOptions.HA_STORAGE_PATH);
        assertThat(storagePath).startsWith("file://");
    }

    @Test
    void testJobManagerCount() {
        // 需求 5.2: THE System SHALL 支持至少2个JobManager实例
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(3)  // 3个JobManager
            .build();

        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);

        assertThat(configurator.getJobManagerCount()).isEqualTo(3);
        assertThat(configurator.getJobManagerCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testFailoverTimeout() {
        // 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)
            .failoverTimeout(30000L)  // 30秒
            .build();

        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);

        assertThat(configurator.getFailoverTimeout()).isEqualTo(30000L);
        assertThat(configurator.getFailoverTimeout()).isLessThanOrEqualTo(30000L);
    }

    @Test
    void testNullConfig() {
        assertThatThrownBy(() -> new HighAvailabilityConfigurator(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HighAvailabilityConfig cannot be null");
    }

    @Test
    void testNullConfiguration() {
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(false)
            .mode("none")
            .build();

        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);

        assertThatThrownBy(() -> configurator.configure(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Configuration cannot be null");
    }

    @Test
    void testIsHAEnabled() {
        // 测试HA启用状态
        HighAvailabilityConfig enabledConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)
            .build();

        HighAvailabilityConfigurator enabledConfigurator = 
            new HighAvailabilityConfigurator(enabledConfig);
        assertThat(enabledConfigurator.isHAEnabled()).isTrue();

        // 测试HA禁用状态
        HighAvailabilityConfig disabledConfig = HighAvailabilityConfig.builder()
            .enabled(false)
            .mode("none")
            .build();

        HighAvailabilityConfigurator disabledConfigurator = 
            new HighAvailabilityConfigurator(disabledConfig);
        assertThat(disabledConfigurator.isHAEnabled()).isFalse();
    }

    @Test
    void testGetHaConfig() {
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)
            .build();

        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);

        assertThat(configurator.getHaConfig()).isEqualTo(haConfig);
    }
}
