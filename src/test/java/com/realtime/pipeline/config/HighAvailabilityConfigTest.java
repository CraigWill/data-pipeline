package com.realtime.pipeline.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 高可用性配置单元测试
 * 
 * 验证需求: 5.1, 5.2, 5.3
 */
class HighAvailabilityConfigTest {

    @Test
    void testValidZooKeeperConfig() {
        // 创建有效的ZooKeeper HA配置
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .build();

        // 验证配置应该通过
        assertThatCode(() -> config.validate())
            .doesNotThrowAnyException();
    }

    @Test
    void testValidKubernetesConfig() {
        // 创建有效的Kubernetes HA配置
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("kubernetes")
            .kubernetesNamespace("flink")
            .kubernetesClusterId("flink-cluster-1")
            .storageDir("/opt/flink/ha")
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .build();

        // 验证配置应该通过
        assertThatCode(() -> config.validate())
            .doesNotThrowAnyException();
    }

    @Test
    void testDisabledHAConfig() {
        // 创建禁用HA的配置
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(false)
            .mode("none")
            .build();

        // 验证配置应该通过
        assertThatCode(() -> config.validate())
            .doesNotThrowAnyException();
    }

    @Test
    void testInvalidMode() {
        // 创建无效模式的配置
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("invalid")
            .build();

        // 验证应该抛出异常
        assertThatThrownBy(() -> config.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HA mode must be");
    }

    @Test
    void testEnabledWithNoneMode() {
        // 创建启用HA但模式为none的配置
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("none")
            .build();

        // 验证应该抛出异常
        assertThatThrownBy(() -> config.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HA is enabled but mode is 'none'");
    }

    @Test
    void testZooKeeperMissingQuorum() {
        // 创建ZooKeeper配置但缺少quorum
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)
            .build();

        // 验证应该抛出异常
        assertThatThrownBy(() -> config.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ZooKeeper quorum is required");
    }

    @Test
    void testKubernetesMissingClusterId() {
        // 创建Kubernetes配置但缺少cluster ID
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("kubernetes")
            .kubernetesNamespace("flink")
            .storageDir("/opt/flink/ha")
            .jobManagerCount(2)
            .build();

        // 验证应该抛出异常
        assertThatThrownBy(() -> config.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Kubernetes cluster ID is required");
    }

    @Test
    void testMissingStorageDir() {
        // 创建配置但缺少存储目录
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .jobManagerCount(2)
            .build();

        // 验证应该抛出异常
        assertThatThrownBy(() -> config.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Storage directory is required");
    }

    @Test
    void testInsufficientJobManagerCount() {
        // 需求 5.2: THE System SHALL 支持至少2个JobManager实例
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(1)  // 少于2个
            .build();

        // 验证应该抛出异常
        assertThatThrownBy(() -> config.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JobManager count must be at least 2");
    }

    @Test
    void testInvalidFailoverTimeout() {
        // 创建配置但故障切换超时无效
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)
            .failoverTimeout(0)  // 无效值
            .build();

        // 验证应该抛出异常
        assertThatThrownBy(() -> config.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failover timeout must be positive");
    }

    @Test
    void testDefaultValues() {
        // 测试默认值
        HighAvailabilityConfig config = HighAvailabilityConfig.builder().build();

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getMode()).isEqualTo("none");
        assertThat(config.getZookeeperPath()).isEqualTo("/flink");
        assertThat(config.getKubernetesNamespace()).isEqualTo("default");
        assertThat(config.getJobManagerCount()).isEqualTo(2);
        assertThat(config.getFailoverTimeout()).isEqualTo(30000L);
        assertThat(config.getZookeeperSessionTimeout()).isEqualTo(60000L);
        assertThat(config.getZookeeperConnectionTimeout()).isEqualTo(15000L);
    }

    @Test
    void testJobManagerCountRequirement() {
        // 需求 5.2: THE System SHALL 支持至少2个JobManager实例
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)
            .build();

        config.validate();
        assertThat(config.getJobManagerCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testFailoverTimeoutRequirement() {
        // 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
        HighAvailabilityConfig config = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)
            .failoverTimeout(30000L)  // 30秒
            .build();

        config.validate();
        assertThat(config.getFailoverTimeout()).isLessThanOrEqualTo(30000L);
    }
}
