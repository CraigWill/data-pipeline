package com.realtime.pipeline.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 扩缩容配置
 * 用于配置动态扩缩容参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalingConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否启用自动扩缩容
     */
    @JsonProperty("autoScalingEnabled")
    @Builder.Default
    private boolean autoScalingEnabled = false;

    /**
     * 最小TaskManager数量
     */
    @JsonProperty("minTaskManagers")
    @Builder.Default
    private int minTaskManagers = 1;

    /**
     * 最大TaskManager数量
     */
    @JsonProperty("maxTaskManagers")
    @Builder.Default
    private int maxTaskManagers = 10;

    /**
     * CPU使用率阈值（百分比），超过此值触发扩容
     */
    @JsonProperty("cpuThreshold")
    @Builder.Default
    private double cpuThreshold = 80.0;

    /**
     * 内存使用率阈值（百分比），超过此值触发扩容
     */
    @JsonProperty("memoryThreshold")
    @Builder.Default
    private double memoryThreshold = 80.0;

    /**
     * 反压阈值（0-1），超过此值触发扩容
     */
    @JsonProperty("backpressureThreshold")
    @Builder.Default
    private double backpressureThreshold = 0.8;

    /**
     * 扩容冷却时间（秒），防止频繁扩容
     */
    @JsonProperty("scaleOutCooldown")
    @Builder.Default
    private int scaleOutCooldown = 300;

    /**
     * 缩容冷却时间（秒），防止频繁缩容
     */
    @JsonProperty("scaleInCooldown")
    @Builder.Default
    private int scaleInCooldown = 600;

    /**
     * 缩容时的排空超时时间（毫秒）
     */
    @JsonProperty("drainTimeout")
    @Builder.Default
    private long drainTimeout = 300000L;

    /**
     * 验证配置的有效性
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (minTaskManagers <= 0) {
            throw new IllegalArgumentException("minTaskManagers must be positive");
        }
        if (maxTaskManagers < minTaskManagers) {
            throw new IllegalArgumentException("maxTaskManagers must be >= minTaskManagers");
        }
        if (cpuThreshold <= 0 || cpuThreshold > 100) {
            throw new IllegalArgumentException("cpuThreshold must be between 0 and 100");
        }
        if (memoryThreshold <= 0 || memoryThreshold > 100) {
            throw new IllegalArgumentException("memoryThreshold must be between 0 and 100");
        }
        if (backpressureThreshold < 0 || backpressureThreshold > 1) {
            throw new IllegalArgumentException("backpressureThreshold must be between 0 and 1");
        }
        if (scaleOutCooldown < 0) {
            throw new IllegalArgumentException("scaleOutCooldown must be non-negative");
        }
        if (scaleInCooldown < 0) {
            throw new IllegalArgumentException("scaleInCooldown must be non-negative");
        }
        if (drainTimeout <= 0) {
            throw new IllegalArgumentException("drainTimeout must be positive");
        }
    }
}
