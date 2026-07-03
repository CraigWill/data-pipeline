package com.realtime.monitor.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * CDC 任务配置
 */
@Data
public class TaskConfig {
    private String id;
    private String name;
    
    @JsonProperty("datasourceId")
    @JsonAlias("datasource_id")
    private String datasourceId;
    
    @JsonProperty("datasourceName")
    @JsonAlias("datasource_name")
    private String datasourceName;
    
    private DatabaseConfig database;
    private String schema;
    private List<String> tables;
    
    @JsonProperty("outputPath")
    @JsonAlias("output_path")
    private String outputPath = "./output/cdc";
    
    private int parallelism = 2;
    
    @JsonProperty("splitSize")
    @JsonAlias("split_size")
    private int splitSize = 8096;
    
    private String created;
    private String savepointPath;  // 恢复时使用的 savepoint 路径（非持久化，仅运行时传递）

    // ── 采集方式：log（默认）/ polling（JDBC 轮询增量）──
    @JsonProperty("sourceMode")
    @JsonAlias("source_mode")
    private String sourceMode = "log";
    @JsonProperty("pollWatermarkColumn")
    @JsonAlias("poll_watermark_column")
    private String pollWatermarkColumn = "ID";
    @JsonProperty("pollWatermarkType")
    @JsonAlias("poll_watermark_type")
    private String pollWatermarkType = "numeric";
    @JsonProperty("pollIntervalMs")
    @JsonAlias("poll_interval_ms")
    private long pollIntervalMs = 5000;
    @JsonProperty("pollStartValue")
    @JsonAlias("poll_start_value")
    private String pollStartValue;
    @JsonProperty("pollOp")
    @JsonAlias("poll_op")
    private String pollOp = "c";
    @JsonProperty("pollMaxBatch")
    @JsonAlias("poll_max_batch")
    private int pollMaxBatch = 5000;

    // 选定的 OSS 连接（用于该任务输出/位点同步）；空 = 用全局默认 OSS
    @JsonProperty("ossConnectionId")
    @JsonAlias("oss_connection_id")
    private String ossConnectionId;
    
    @Data
    public static class DatabaseConfig {
        private String host;
        private int port = 1521;
        private String username;
        private String password;
        private String sid;
        private String schema;
    }
}
