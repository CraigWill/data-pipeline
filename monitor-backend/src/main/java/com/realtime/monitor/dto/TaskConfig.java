package com.realtime.monitor.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

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
