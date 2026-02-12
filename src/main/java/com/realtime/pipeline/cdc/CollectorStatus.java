package com.realtime.pipeline.cdc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * CDC采集器状态
 * 表示CDC采集器的当前运行状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectorStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否正在运行
     */
    private boolean running;

    /**
     * 状态描述: RUNNING, STOPPED, ERROR
     */
    private String status;

    /**
     * 已采集记录数
     */
    private long recordsCollected;

    /**
     * 最后事件时间戳
     */
    private long lastEventTime;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
}
