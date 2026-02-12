package com.realtime.pipeline.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 监控配置
 * 用于配置监控和告警参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否启用监控，默认true
     */
    @JsonProperty("enabled")
    @Builder.Default
    private boolean enabled = true;

    /**
     * Metrics报告间隔（秒），默认60秒
     */
    @JsonProperty("metricsInterval")
    @Builder.Default
    private int metricsInterval = 60;

    /**
     * 延迟告警阈值（毫秒），默认60秒
     */
    @JsonProperty("latencyThreshold")
    @Builder.Default
    private long latencyThreshold = 60000L;

    /**
     * Checkpoint失败率告警阈值（百分比），默认10%
     */
    @JsonProperty("checkpointFailureRateThreshold")
    @Builder.Default
    private double checkpointFailureRateThreshold = 0.1;

    /**
     * 负载告警阈值（百分比），默认80%
     */
    @JsonProperty("loadThreshold")
    @Builder.Default
    private double loadThreshold = 0.8;

    /**
     * 反压告警阈值（0-1），默认0.8
     */
    @JsonProperty("backpressureThreshold")
    @Builder.Default
    private double backpressureThreshold = 0.8;

    /**
     * 健康检查端口，默认8081
     */
    @JsonProperty("healthCheckPort")
    @Builder.Default
    private int healthCheckPort = 8081;

    /**
     * 告警通知方式: log, email, webhook
     */
    @JsonProperty("alertMethod")
    @Builder.Default
    private String alertMethod = "log";

    /**
     * Webhook URL（当alertMethod为webhook时）
     */
    @JsonProperty("webhookUrl")
    private String webhookUrl;

    /**
     * 验证配置的有效性
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (metricsInterval <= 0) {
            throw new IllegalArgumentException("Monitoring metricsInterval must be positive");
        }
        if (latencyThreshold <= 0) {
            throw new IllegalArgumentException("Monitoring latencyThreshold must be positive");
        }
        if (checkpointFailureRateThreshold < 0 || checkpointFailureRateThreshold > 1) {
            throw new IllegalArgumentException("Monitoring checkpointFailureRateThreshold must be between 0 and 1");
        }
        if (loadThreshold < 0 || loadThreshold > 1) {
            throw new IllegalArgumentException("Monitoring loadThreshold must be between 0 and 1");
        }
        if (backpressureThreshold < 0 || backpressureThreshold > 1) {
            throw new IllegalArgumentException("Monitoring backpressureThreshold must be between 0 and 1");
        }
        if (healthCheckPort <= 0 || healthCheckPort > 65535) {
            throw new IllegalArgumentException("Monitoring healthCheckPort must be between 1 and 65535");
        }
        if (!alertMethod.matches("log|email|webhook")) {
            throw new IllegalArgumentException("Monitoring alertMethod must be 'log', 'email', or 'webhook'");
        }
        if ("webhook".equals(alertMethod) && (webhookUrl == null || webhookUrl.trim().isEmpty())) {
            throw new IllegalArgumentException("Monitoring webhookUrl is required when alertMethod is 'webhook'");
        }
    }
}
