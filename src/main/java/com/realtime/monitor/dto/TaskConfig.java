package com.realtime.monitor.dto;

import lombok.Data;
import java.util.List;

/**
 * CDC 任务配置
 */
@Data
public class TaskConfig {
    private String id;
    private String name;
    private String datasourceId;
    private String datasourceName;
    private DatabaseConfig database;
    private String schema;
    private List<String> tables;
    private String outputPath = "./output/cdc";
    private int parallelism = 2;
    private int splitSize = 8096;
    private String created;
    private String status = "CREATED";  // CREATED, RUNNING, STOPPED, FAILED
    private String flinkJobId;
    
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
