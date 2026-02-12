package com.realtime.pipeline.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管道配置
 * 包含所有子系统的配置信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 数据库配置
     */
    @JsonProperty("database")
    private DatabaseConfig database;

    /**
     * DataHub配置
     */
    @JsonProperty("datahub")
    private DataHubConfig datahub;

    /**
     * Flink配置
     */
    @JsonProperty("flink")
    private FlinkConfig flink;

    /**
     * 输出配置
     */
    @JsonProperty("output")
    private OutputConfig output;

    /**
     * 监控配置
     */
    @JsonProperty("monitoring")
    private MonitoringConfig monitoring;

    /**
     * 高可用性配置
     */
    @JsonProperty("highAvailability")
    private HighAvailabilityConfig highAvailability;

    /**
     * 验证配置的有效性
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (database == null) {
            throw new IllegalArgumentException("Database configuration is required");
        }
        if (datahub == null) {
            throw new IllegalArgumentException("DataHub configuration is required");
        }
        if (flink == null) {
            throw new IllegalArgumentException("Flink configuration is required");
        }
        if (output == null) {
            throw new IllegalArgumentException("Output configuration is required");
        }

        database.validate();
        datahub.validate();
        flink.validate();
        output.validate();
        
        if (monitoring != null) {
            monitoring.validate();
        }
        
        if (highAvailability != null) {
            highAvailability.validate();
        }
    }
}
