package com.realtime.pipeline.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 死信记录数据模型
 * 表示处理失败的记录及其上下文信息
 */
@Data
@Builder
public class DeadLetterRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 死信记录唯一标识符
     */
    @JsonProperty("recordId")
    private String recordId;

    /**
     * 原始事件ID
     */
    @JsonProperty("eventId")
    private String eventId;

    /**
     * 失败时间戳（毫秒）
     */
    @JsonProperty("failureTimestamp")
    private long failureTimestamp;

    /**
     * 失败原因
     */
    @JsonProperty("failureReason")
    private String failureReason;

    /**
     * 异常堆栈信息
     */
    @JsonProperty("stackTrace")
    private String stackTrace;

    /**
     * 失败的组件名称（例如：EventProcessor, FileSink）
     */
    @JsonProperty("component")
    private String component;

    /**
     * 失败的操作类型（例如：PROCESS, WRITE）
     */
    @JsonProperty("operationType")
    private String operationType;

    /**
     * 重试次数
     */
    @JsonProperty("retryCount")
    private int retryCount;

    /**
     * 原始数据（ChangeEvent或ProcessedEvent的JSON表示）
     */
    @JsonProperty("originalData")
    private String originalData;

    /**
     * 数据类型（CHANGE_EVENT或PROCESSED_EVENT）
     */
    @JsonProperty("dataType")
    private String dataType;

    /**
     * 上下文信息（额外的调试信息）
     */
    @JsonProperty("context")
    private Map<String, String> context;

    /**
     * 是否已重新处理
     */
    @JsonProperty("reprocessed")
    private boolean reprocessed;

    /**
     * 重新处理时间戳（毫秒）
     */
    @JsonProperty("reprocessTimestamp")
    private Long reprocessTimestamp;

    @JsonCreator
    public DeadLetterRecord(
            @JsonProperty("recordId") String recordId,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("failureTimestamp") long failureTimestamp,
            @JsonProperty("failureReason") String failureReason,
            @JsonProperty("stackTrace") String stackTrace,
            @JsonProperty("component") String component,
            @JsonProperty("operationType") String operationType,
            @JsonProperty("retryCount") int retryCount,
            @JsonProperty("originalData") String originalData,
            @JsonProperty("dataType") String dataType,
            @JsonProperty("context") Map<String, String> context,
            @JsonProperty("reprocessed") boolean reprocessed,
            @JsonProperty("reprocessTimestamp") Long reprocessTimestamp) {
        this.recordId = recordId;
        this.eventId = eventId;
        this.failureTimestamp = failureTimestamp;
        this.failureReason = failureReason;
        this.stackTrace = stackTrace;
        this.component = component;
        this.operationType = operationType;
        this.retryCount = retryCount;
        this.originalData = originalData;
        this.dataType = dataType;
        this.context = context;
        this.reprocessed = reprocessed;
        this.reprocessTimestamp = reprocessTimestamp;
    }

    /**
     * 获取失败时长（从失败到现在的时间，毫秒）
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public long getFailureDuration() {
        return System.currentTimeMillis() - failureTimestamp;
    }

    /**
     * 判断是否可以重新处理
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean canReprocess() {
        return !reprocessed;
    }
}
