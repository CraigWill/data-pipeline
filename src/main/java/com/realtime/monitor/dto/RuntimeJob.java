package com.realtime.monitor.dto;

import lombok.Data;
import java.util.List;

/**
 * 运行时作业 DTO
 */
@Data
public class RuntimeJob {
    private String id;
    private String taskId;
    private String flinkJobId;
    private String jobName;
    private String status;  // SUBMITTING, RUNNING, FINISHED, FAILED, CANCELED
    private String schemaName;
    private List<String> tables;
    private Integer parallelism;
    private String submitTime;
    private String startTime;
    private String endTime;
    private String errorMessage;
    private String lastSavepointPath;   // 最近一次成功的 savepoint 路径
    private String lastSavepointTime;   // 最近一次 savepoint 时间
}
