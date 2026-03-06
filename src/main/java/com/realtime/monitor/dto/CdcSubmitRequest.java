package com.realtime.monitor.dto;

import lombok.Data;
import java.util.List;

/**
 * CDC 任务提交请求
 */
@Data
public class CdcSubmitRequest {
    private String hostname;
    private int port;
    private String username;
    private String password;
    private String database;
    private String schema;
    private List<String> tables;
    private String outputPath = "./output/cdc";
    private int parallelism = 2;
    private int splitSize = 8096;
    private String startupMode = "latest";
    private String jobName;
}
