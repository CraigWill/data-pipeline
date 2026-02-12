package com.realtime.pipeline.flink.ha;

import com.realtime.pipeline.config.HighAvailabilityConfig;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.HighAvailabilityOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * 高可用性配置器
 * 负责配置Flink JobManager的高可用性
 * 
 * 支持两种HA模式:
 * 1. ZooKeeper模式: 使用ZooKeeper进行领导者选举和元数据存储
 * 2. Kubernetes模式: 使用Kubernetes原生HA机制
 * 
 * 验证需求: 5.1, 5.2, 5.3
 */
public class HighAvailabilityConfigurator {
    private static final Logger logger = LoggerFactory.getLogger(HighAvailabilityConfigurator.class);

    private final HighAvailabilityConfig haConfig;

    /**
     * 构造函数
     * @param haConfig 高可用性配置
     */
    public HighAvailabilityConfigurator(HighAvailabilityConfig haConfig) {
        if (haConfig == null) {
            throw new IllegalArgumentException("HighAvailabilityConfig cannot be null");
        }
        this.haConfig = haConfig;
        // 验证配置有效性
        haConfig.validate();
    }

    /**
     * 配置高可用性
     * 
     * 需求 5.1: THE System SHALL 支持Flink JobManager的高可用配置
     * 需求 5.2: THE System SHALL 支持至少2个JobManager实例
     * 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
     * 
     * @param config Flink配置对象
     */
    public void configure(Configuration config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        if (!haConfig.isEnabled()) {
            logger.info("High availability is disabled");
            config.setString(HighAvailabilityOptions.HA_MODE, "NONE");
            return;
        }

        logger.info("Configuring high availability with mode: {}", haConfig.getMode());

        String mode = haConfig.getMode().toLowerCase();
        switch (mode) {
            case "zookeeper":
                configureZooKeeperHA(config);
                break;
            case "kubernetes":
                configureKubernetesHA(config);
                break;
            case "none":
                config.setString(HighAvailabilityOptions.HA_MODE, "NONE");
                logger.info("HA mode set to NONE");
                break;
            default:
                throw new IllegalArgumentException("Unsupported HA mode: " + mode);
        }

        // 配置存储目录（所有模式都需要）
        if (haConfig.isEnabled() && !"none".equals(mode)) {
            configureStorageDirectory(config);
        }

        logger.info("High availability configured successfully");
    }

    /**
     * 配置ZooKeeper高可用模式
     * 
     * @param config Flink配置对象
     */
    private void configureZooKeeperHA(Configuration config) {
        logger.info("Configuring ZooKeeper HA mode");

        // 设置HA模式为ZooKeeper
        config.setString(HighAvailabilityOptions.HA_MODE, "zookeeper");
        logger.info("Set HA mode to zookeeper");

        // 设置ZooKeeper quorum
        String quorum = haConfig.getZookeeperQuorum();
        config.setString(HighAvailabilityOptions.HA_ZOOKEEPER_QUORUM, quorum);
        logger.info("Set ZooKeeper quorum: {}", quorum);

        // 设置ZooKeeper根路径
        String zkPath = haConfig.getZookeeperPath();
        config.setString(HighAvailabilityOptions.HA_ZOOKEEPER_ROOT, zkPath);
        logger.info("Set ZooKeeper root path: {}", zkPath);

        // 设置ZooKeeper会话超时
        long sessionTimeout = haConfig.getZookeeperSessionTimeout();
        config.setString(HighAvailabilityOptions.ZOOKEEPER_SESSION_TIMEOUT.key(), 
            sessionTimeout + "ms");
        logger.info("Set ZooKeeper session timeout: {} ms", sessionTimeout);

        // 设置ZooKeeper连接超时
        long connectionTimeout = haConfig.getZookeeperConnectionTimeout();
        config.setString(HighAvailabilityOptions.ZOOKEEPER_CONNECTION_TIMEOUT.key(), 
            connectionTimeout + "ms");
        logger.info("Set ZooKeeper connection timeout: {} ms", connectionTimeout);

        logger.info("ZooKeeper HA configuration completed");
    }

    /**
     * 配置Kubernetes高可用模式
     * 
     * @param config Flink配置对象
     */
    private void configureKubernetesHA(Configuration config) {
        logger.info("Configuring Kubernetes HA mode");

        // 设置HA模式为Kubernetes
        config.setString(HighAvailabilityOptions.HA_MODE, "kubernetes");
        logger.info("Set HA mode to kubernetes");

        // 设置Kubernetes命名空间
        String namespace = haConfig.getKubernetesNamespace();
        config.setString("kubernetes.namespace", namespace);
        logger.info("Set Kubernetes namespace: {}", namespace);

        // 设置Kubernetes集群ID
        String clusterId = haConfig.getKubernetesClusterId();
        config.setString("kubernetes.cluster-id", clusterId);
        logger.info("Set Kubernetes cluster ID: {}", clusterId);

        logger.info("Kubernetes HA configuration completed");
    }

    /**
     * 配置存储目录
     * 用于存储HA元数据（JobManager元数据、已完成的checkpoint等）
     * 
     * @param config Flink配置对象
     */
    private void configureStorageDirectory(Configuration config) {
        String storageDir = haConfig.getStorageDir();
        
        // 确保存储目录有正确的URI scheme
        if (!storageDir.contains("://")) {
            storageDir = "file://" + storageDir;
        }

        config.setString(HighAvailabilityOptions.HA_STORAGE_PATH, storageDir);
        logger.info("Set HA storage directory: {}", storageDir);
    }

    /**
     * 获取配置的HighAvailabilityConfig
     * @return HighAvailabilityConfig
     */
    public HighAvailabilityConfig getHaConfig() {
        return haConfig;
    }

    /**
     * 检查HA是否已启用
     * @return true如果HA已启用
     */
    public boolean isHAEnabled() {
        return haConfig.isEnabled() && !"none".equals(haConfig.getMode());
    }

    /**
     * 获取JobManager数量
     * @return JobManager数量
     */
    public int getJobManagerCount() {
        return haConfig.getJobManagerCount();
    }

    /**
     * 获取故障切换超时时间（毫秒）
     * @return 故障切换超时时间
     */
    public long getFailoverTimeout() {
        return haConfig.getFailoverTimeout();
    }
}
