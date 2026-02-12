package com.realtime.pipeline.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 采集指标数据模型
 * 用于记录CDC采集器的运行指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectorMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 已采集记录数
     */
    private long recordsCollected;

    /**
     * 已发送记录数
     */
    private long recordsSent;

    /**
     * 失败记录数
     */
    private long recordsFailed;

    /**
     * 采集速率（记录/秒）
     */
    private double collectRate;

    /**
     * 最后事件时间
     */
    private long lastEventTime;

    /**
     * 状态: RUNNING, STOPPED, ERROR
     */
    private String status;

    /**
     * 计算成功率
     */
    public double getSuccessRate() {
        if (recordsCollected == 0) {
            return 1.0;
        }
        return (double) recordsSent / recordsCollected;
    }

    /**
     * 计算失败率
     */
    public double getFailureRate() {
        if (recordsCollected == 0) {
            return 0.0;
        }
        return (double) recordsFailed / recordsCollected;
    }
}
