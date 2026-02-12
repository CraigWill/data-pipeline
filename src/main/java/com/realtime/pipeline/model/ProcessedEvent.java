package com.realtime.pipeline.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 处理后事件数据模型
 * 表示经过Flink处理后的事件数据
 */
@Data
@Builder
public class ProcessedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型: INSERT, UPDATE, DELETE
     */
    @JsonProperty("eventType")
    private String eventType;

    /**
     * 数据库名称
     */
    @JsonProperty("database")
    private String database;

    /**
     * 表名称
     */
    @JsonProperty("table")
    private String table;

    /**
     * 原始事件时间戳（毫秒）
     */
    @JsonProperty("timestamp")
    private long timestamp;

    /**
     * 处理时间戳（毫秒）
     */
    @JsonProperty("processTime")
    private long processTime;

    /**
     * 处理后的数据
     */
    @JsonProperty("data")
    private Map<String, Object> data;

    /**
     * 分区信息
     */
    @JsonProperty("partition")
    private String partition;

    /**
     * 事件唯一标识符
     */
    @JsonProperty("eventId")
    private String eventId;

    @JsonCreator
    public ProcessedEvent(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("database") String database,
            @JsonProperty("table") String table,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("processTime") long processTime,
            @JsonProperty("data") Map<String, Object> data,
            @JsonProperty("partition") String partition,
            @JsonProperty("eventId") String eventId) {
        this.eventType = eventType;
        this.database = database;
        this.table = table;
        this.timestamp = timestamp;
        this.processTime = processTime;
        this.data = data;
        this.partition = partition;
        this.eventId = eventId;
    }

    /**
     * 计算处理延迟（毫秒）
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public long getProcessingLatency() {
        return processTime - timestamp;
    }

    /**
     * 获取表的完整名称（database.table）
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getFullTableName() {
        return database + "." + table;
    }
}
