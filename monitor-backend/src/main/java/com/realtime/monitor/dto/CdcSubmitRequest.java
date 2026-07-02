package com.realtime.monitor.dto;

import java.util.List;

import lombok.Data;

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
    private String savepointPath;  // 从 savepoint 恢复时使用
    private String dbType = "ORACLE";  // 数据库类型: ORACLE, MYSQL, OCEANBASE, OCEANBASE_ORACLE

    // ── 采集方式：log（默认，日志级 CDC，经 oblogproxy）或 polling（JDBC 轮询增量）──
    private String sourceMode = "log";
    // 轮询模式参数（sourceMode=polling 时生效）
    private String pollWatermarkColumn = "ID";   // 水位列（建议唯一单调列，如自增主键）
    private String pollWatermarkType = "numeric"; // numeric | timestamp
    private long pollIntervalMs = 5000;
    private String pollStartValue;                // 可空：数值起点或起始 epoch 毫秒
    private String pollOp = "c";                  // CSV 操作标签
    private int pollMaxBatch = 5000;
}
