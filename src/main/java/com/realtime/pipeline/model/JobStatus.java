package com.realtime.pipeline.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 作业状态数据模型
 * 用于记录Flink作业的运行状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 作业ID
     */
    private String jobId;

    /**
     * 作业名称
     */
    private String jobName;

    /**
     * 状态: RUNNING, FINISHED, FAILED, CANCELED
     */
    private String state;

    /**
     * 启动时间
     */
    private long startTime;

    /**
     * 运行时长（毫秒）
     */
    private long duration;

    /**
     * 并行度
     */
    private int parallelism;

    /**
     * 指标数据
     */
    private Map<String, Object> metrics;

    /**
     * 判断作业是否正在运行
     */
    public boolean isRunning() {
        return "RUNNING".equals(state);
    }

    /**
     * 判断作业是否已完成
     */
    public boolean isFinished() {
        return "FINISHED".equals(state);
    }

    /**
     * 判断作业是否失败
     */
    public boolean isFailed() {
        return "FAILED".equals(state);
    }

    /**
     * 判断作业是否被取消
     */
    public boolean isCanceled() {
        return "CANCELED".equals(state);
    }
}
