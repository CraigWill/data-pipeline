package com.realtime.pipeline.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Flink配置
 * 用于配置Flink执行环境和Checkpoint参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlinkConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 默认并行度
     */
    @JsonProperty("parallelism")
    @Builder.Default
    private int parallelism = 1;

    /**
     * Checkpoint间隔（毫秒），默认5分钟
     */
    @JsonProperty("checkpointInterval")
    @Builder.Default
    private long checkpointInterval = 300000L;

    /**
     * Checkpoint超时时间（毫秒），默认10分钟
     */
    @JsonProperty("checkpointTimeout")
    @Builder.Default
    private long checkpointTimeout = 600000L;

    /**
     * 两次Checkpoint之间的最小间隔（毫秒），默认1分钟
     */
    @JsonProperty("minPauseBetweenCheckpoints")
    @Builder.Default
    private long minPauseBetweenCheckpoints = 60000L;

    /**
     * 最大并发Checkpoint数，默认1
     */
    @JsonProperty("maxConcurrentCheckpoints")
    @Builder.Default
    private int maxConcurrentCheckpoints = 1;

    /**
     * 保留的Checkpoint数量，默认3
     */
    @JsonProperty("retainedCheckpoints")
    @Builder.Default
    private int retainedCheckpoints = 3;

    /**
     * 状态后端类型: hashmap, rocksdb
     */
    @JsonProperty("stateBackendType")
    @Builder.Default
    private String stateBackendType = "hashmap";

    /**
     * Checkpoint存储目录
     */
    @JsonProperty("checkpointDir")
    private String checkpointDir;

    /**
     * 容忍的Checkpoint失败次数，默认3
     */
    @JsonProperty("tolerableCheckpointFailures")
    @Builder.Default
    private int tolerableCheckpointFailures = 3;

    /**
     * 重启策略: fixed-delay, failure-rate, none
     */
    @JsonProperty("restartStrategy")
    @Builder.Default
    private String restartStrategy = "fixed-delay";

    /**
     * 重启尝试次数（fixed-delay策略），默认3
     */
    @JsonProperty("restartAttempts")
    @Builder.Default
    private int restartAttempts = 3;

    /**
     * 重启延迟（毫秒，fixed-delay策略），默认10秒
     */
    @JsonProperty("restartDelay")
    @Builder.Default
    private long restartDelay = 10000L;

    /**
     * Checkpoint模式: at-least-once, exactly-once
     * 默认为at-least-once以满足需求9.1
     * 当配置幂等性Sink时可以使用exactly-once（需求9.2）
     */
    @JsonProperty("checkpointingMode")
    @Builder.Default
    private String checkpointingMode = "at-least-once";

    /**
     * JobManager主机地址
     */
    @JsonProperty("jobManagerHost")
    @Builder.Default
    private String jobManagerHost = "localhost";

    /**
     * JobManager REST端口
     */
    @JsonProperty("jobManagerPort")
    @Builder.Default
    private int jobManagerPort = 8081;

    /**
     * 验证配置的有效性
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("Flink parallelism must be positive");
        }
        if (checkpointInterval <= 0) {
            throw new IllegalArgumentException("Flink checkpointInterval must be positive");
        }
        if (checkpointTimeout <= 0) {
            throw new IllegalArgumentException("Flink checkpointTimeout must be positive");
        }
        if (minPauseBetweenCheckpoints < 0) {
            throw new IllegalArgumentException("Flink minPauseBetweenCheckpoints must be non-negative");
        }
        if (maxConcurrentCheckpoints <= 0) {
            throw new IllegalArgumentException("Flink maxConcurrentCheckpoints must be positive");
        }
        if (retainedCheckpoints <= 0) {
            throw new IllegalArgumentException("Flink retainedCheckpoints must be positive");
        }
        if (!stateBackendType.matches("hashmap|rocksdb")) {
            throw new IllegalArgumentException("Flink stateBackendType must be 'hashmap' or 'rocksdb'");
        }
        if (checkpointDir == null || checkpointDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Flink checkpointDir is required");
        }
        if (tolerableCheckpointFailures < 0) {
            throw new IllegalArgumentException("Flink tolerableCheckpointFailures must be non-negative");
        }
        if (!restartStrategy.matches("fixed-delay|failure-rate|none")) {
            throw new IllegalArgumentException("Flink restartStrategy must be 'fixed-delay', 'failure-rate', or 'none'");
        }
        if (restartAttempts < 0) {
            throw new IllegalArgumentException("Flink restartAttempts must be non-negative");
        }
        if (restartDelay < 0) {
            throw new IllegalArgumentException("Flink restartDelay must be non-negative");
        }
        if (!checkpointingMode.matches("at-least-once|exactly-once")) {
            throw new IllegalArgumentException("Flink checkpointingMode must be 'at-least-once' or 'exactly-once'");
        }
    }
}
