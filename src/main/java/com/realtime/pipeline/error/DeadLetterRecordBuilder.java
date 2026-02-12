package com.realtime.pipeline.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 死信记录构建器
 * 提供便捷的方法从异常和事件创建死信记录
 */
@Slf4j
public class DeadLetterRecordBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String recordId;
    private String eventId;
    private long failureTimestamp;
    private String failureReason;
    private String stackTrace;
    private String component;
    private String operationType;
    private int retryCount;
    private String originalData;
    private String dataType;
    private Map<String, String> context;

    private DeadLetterRecordBuilder() {
        this.recordId = UUID.randomUUID().toString();
        this.failureTimestamp = System.currentTimeMillis();
        this.context = new HashMap<>();
        this.retryCount = 0;
    }

    /**
     * 创建新的构建器实例
     */
    public static DeadLetterRecordBuilder builder() {
        return new DeadLetterRecordBuilder();
    }

    /**
     * 从ChangeEvent创建死信记录
     */
    public static DeadLetterRecordBuilder fromChangeEvent(ChangeEvent event) {
        DeadLetterRecordBuilder builder = new DeadLetterRecordBuilder();
        builder.eventId = event.getEventId();
        builder.dataType = "CHANGE_EVENT";
        try {
            builder.originalData = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ChangeEvent", e);
            builder.originalData = event.toString();
        }
        return builder;
    }

    /**
     * 从ProcessedEvent创建死信记录
     */
    public static DeadLetterRecordBuilder fromProcessedEvent(ProcessedEvent event) {
        DeadLetterRecordBuilder builder = new DeadLetterRecordBuilder();
        builder.eventId = event.getEventId();
        builder.dataType = "PROCESSED_EVENT";
        try {
            builder.originalData = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ProcessedEvent", e);
            builder.originalData = event.toString();
        }
        return builder;
    }

    /**
     * 设置记录ID
     */
    public DeadLetterRecordBuilder recordId(String recordId) {
        this.recordId = recordId;
        return this;
    }

    /**
     * 设置事件ID
     */
    public DeadLetterRecordBuilder eventId(String eventId) {
        this.eventId = eventId;
        return this;
    }

    /**
     * 设置失败时间戳
     */
    public DeadLetterRecordBuilder failureTimestamp(long failureTimestamp) {
        this.failureTimestamp = failureTimestamp;
        return this;
    }

    /**
     * 设置失败原因
     */
    public DeadLetterRecordBuilder failureReason(String failureReason) {
        this.failureReason = failureReason;
        return this;
    }

    /**
     * 从异常设置失败原因和堆栈信息
     */
    public DeadLetterRecordBuilder exception(Throwable throwable) {
        if (throwable != null) {
            this.failureReason = throwable.getMessage();
            this.stackTrace = getStackTraceAsString(throwable);
        }
        return this;
    }

    /**
     * 设置堆栈信息
     */
    public DeadLetterRecordBuilder stackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        return this;
    }

    /**
     * 设置组件名称
     */
    public DeadLetterRecordBuilder component(String component) {
        this.component = component;
        return this;
    }

    /**
     * 设置操作类型
     */
    public DeadLetterRecordBuilder operationType(String operationType) {
        this.operationType = operationType;
        return this;
    }

    /**
     * 设置重试次数
     */
    public DeadLetterRecordBuilder retryCount(int retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    /**
     * 设置原始数据
     */
    public DeadLetterRecordBuilder originalData(String originalData) {
        this.originalData = originalData;
        return this;
    }

    /**
     * 设置数据类型
     */
    public DeadLetterRecordBuilder dataType(String dataType) {
        this.dataType = dataType;
        return this;
    }

    /**
     * 添加上下文信息
     */
    public DeadLetterRecordBuilder addContext(String key, String value) {
        this.context.put(key, value);
        return this;
    }

    /**
     * 设置上下文信息
     */
    public DeadLetterRecordBuilder context(Map<String, String> context) {
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        return this;
    }

    /**
     * 构建死信记录
     */
    public DeadLetterRecord build() {
        return DeadLetterRecord.builder()
                .recordId(recordId)
                .eventId(eventId)
                .failureTimestamp(failureTimestamp)
                .failureReason(failureReason)
                .stackTrace(stackTrace)
                .component(component)
                .operationType(operationType)
                .retryCount(retryCount)
                .originalData(originalData)
                .dataType(dataType)
                .context(context)
                .reprocessed(false)
                .reprocessTimestamp(null)
                .build();
    }

    /**
     * 将异常堆栈转换为字符串
     */
    private static String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
