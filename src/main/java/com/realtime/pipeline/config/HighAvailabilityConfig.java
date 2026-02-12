package com.realtime.pipeline.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 高可用性配置
 * 用于配置Flink JobManager的高可用性
 * 
 * 验证需求: 5.1, 5.2, 5.3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighAvailabilityConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否启用高可用性，默认false
     */
    @JsonProperty("enabled")
    @Builder.Default
    private boolean enabled = false;

    /**
     * 高可用模式: zookeeper, kubernetes, none
     * 默认为none（不启用HA）
     */
    @JsonProperty("mode")
    @Builder.Default
    private String mode = "none";

    /**
     * ZooKeeper连接字符串（mode=zookeeper时必需）
     * 格式: host1:port1,host2:port2,host3:port3
     */
    @JsonProperty("zookeeperQuorum")
    private String zookeeperQuorum;

    /**
     * ZooKeeper根路径，默认/flink
     */
    @JsonProperty("zookeeperPath")
    @Builder.Default
    private String zookeeperPath = "/flink";

    /**
     * Kubernetes命名空间（mode=kubernetes时使用）
     */
    @JsonProperty("kubernetesNamespace")
    @Builder.Default
    private String kubernetesNamespace = "default";

    /**
     * Kubernetes集群ID
     */
    @JsonProperty("kubernetesClusterId")
    private String kubernetesClusterId;

    /**
     * 存储目录（用于存储HA元数据）
     * 必须是所有JobManager都能访问的共享存储
     */
    @JsonProperty("storageDir")
    private String storageDir;

    /**
     * JobManager数量，默认2（主备模式）
     * 需求 5.2: THE System SHALL 支持至少2个JobManager实例
     */
    @JsonProperty("jobManagerCount")
    @Builder.Default
    private int jobManagerCount = 2;

    /**
     * 故障切换超时时间（毫秒），默认30秒
     * 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
     */
    @JsonProperty("failoverTimeout")
    @Builder.Default
    private long failoverTimeout = 30000L;

    /**
     * ZooKeeper会话超时时间（毫秒），默认60秒
     */
    @JsonProperty("zookeeperSessionTimeout")
    @Builder.Default
    private long zookeeperSessionTimeout = 60000L;

    /**
     * ZooKeeper连接超时时间（毫秒），默认15秒
     */
    @JsonProperty("zookeeperConnectionTimeout")
    @Builder.Default
    private long zookeeperConnectionTimeout = 15000L;

    /**
     * 验证配置的有效性
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (!mode.matches("none|zookeeper|kubernetes")) {
            throw new IllegalArgumentException("HA mode must be 'none', 'zookeeper', or 'kubernetes'");
        }

        if (enabled && "none".equals(mode)) {
            throw new IllegalArgumentException("HA is enabled but mode is 'none'");
        }

        if (enabled) {
            if ("zookeeper".equals(mode)) {
                if (zookeeperQuorum == null || zookeeperQuorum.trim().isEmpty()) {
                    throw new IllegalArgumentException("ZooKeeper quorum is required when HA mode is 'zookeeper'");
                }
                if (zookeeperPath == null || zookeeperPath.trim().isEmpty()) {
                    throw new IllegalArgumentException("ZooKeeper path is required when HA mode is 'zookeeper'");
                }
                if (zookeeperSessionTimeout <= 0) {
                    throw new IllegalArgumentException("ZooKeeper session timeout must be positive");
                }
                if (zookeeperConnectionTimeout <= 0) {
                    throw new IllegalArgumentException("ZooKeeper connection timeout must be positive");
                }
            } else if ("kubernetes".equals(mode)) {
                if (kubernetesNamespace == null || kubernetesNamespace.trim().isEmpty()) {
                    throw new IllegalArgumentException("Kubernetes namespace is required when HA mode is 'kubernetes'");
                }
                if (kubernetesClusterId == null || kubernetesClusterId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Kubernetes cluster ID is required when HA mode is 'kubernetes'");
                }
            }

            if (storageDir == null || storageDir.trim().isEmpty()) {
                throw new IllegalArgumentException("Storage directory is required when HA is enabled");
            }

            if (jobManagerCount < 2) {
                throw new IllegalArgumentException("JobManager count must be at least 2 for high availability");
            }

            if (failoverTimeout <= 0) {
                throw new IllegalArgumentException("Failover timeout must be positive");
            }
        }
    }
}
