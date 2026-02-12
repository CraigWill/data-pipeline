package com.realtime.pipeline.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 输出配置
 * 用于配置文件输出参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutputConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 输出目录路径
     */
    @JsonProperty("path")
    private String path;

    /**
     * 输出格式: json, parquet, csv
     */
    @JsonProperty("format")
    @Builder.Default
    private String format = "json";

    /**
     * 文件大小阈值（字节），默认1GB
     */
    @JsonProperty("rollingSizeBytes")
    @Builder.Default
    private long rollingSizeBytes = 1024L * 1024L * 1024L;

    /**
     * 时间间隔阈值（毫秒），默认1小时
     */
    @JsonProperty("rollingIntervalMs")
    @Builder.Default
    private long rollingIntervalMs = 3600000L;

    /**
     * 压缩算法: none, gzip, snappy
     */
    @JsonProperty("compression")
    @Builder.Default
    private String compression = "none";

    /**
     * 写入失败最大重试次数，默认3
     */
    @JsonProperty("maxRetries")
    @Builder.Default
    private int maxRetries = 3;

    /**
     * 重试间隔（秒），默认2秒
     */
    @JsonProperty("retryBackoff")
    @Builder.Default
    private int retryBackoff = 2;

    /**
     * 验证配置的有效性
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Output path is required");
        }
        if (!format.matches("json|parquet|csv")) {
            throw new IllegalArgumentException("Output format must be 'json', 'parquet', or 'csv'");
        }
        if (rollingSizeBytes <= 0) {
            throw new IllegalArgumentException("Output rollingSizeBytes must be positive");
        }
        if (rollingIntervalMs <= 0) {
            throw new IllegalArgumentException("Output rollingIntervalMs must be positive");
        }
        if (!compression.matches("none|gzip|snappy")) {
            throw new IllegalArgumentException("Output compression must be 'none', 'gzip', or 'snappy'");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Output maxRetries must be non-negative");
        }
        if (retryBackoff <= 0) {
            throw new IllegalArgumentException("Output retryBackoff must be positive");
        }
    }
}
