package com.realtime.pipeline.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 变更事件数据模型
 * 表示从数据库捕获的变更数据（CDC事件）
 */
@Data
@Builder
public class ChangeEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型: INSERT, UPDATE, DELETE
     */
    @JsonProperty("type")
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
     * 事件时间戳（毫秒）
     */
    @JsonProperty("timestamp")
    private long timestamp;

    /**
     * 变更前数据（仅UPDATE和DELETE）
     */
    @JsonProperty("before")
    private Map<String, Object> before;

    /**
     * 变更后数据（仅INSERT和UPDATE）
     */
    @JsonProperty("after")
    private Map<String, Object> after;

    /**
     * 主键字段列表
     */
    @JsonProperty("primaryKeys")
    private List<String> primaryKeys;

    /**
     * 事件唯一标识符
     */
    @JsonProperty("eventId")
    private String eventId;

    @JsonCreator
    public ChangeEvent(
            @JsonProperty("type") String eventType,
            @JsonProperty("database") String database,
            @JsonProperty("table") String table,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("before") Map<String, Object> before,
            @JsonProperty("after") Map<String, Object> after,
            @JsonProperty("primaryKeys") List<String> primaryKeys,
            @JsonProperty("eventId") String eventId) {
        this.eventType = eventType;
        this.database = database;
        this.table = table;
        this.timestamp = timestamp;
        this.before = before;
        this.after = after;
        this.primaryKeys = primaryKeys;
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
    }

    /**
     * 获取事件的数据内容（根据事件类型返回before或after）
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Map<String, Object> getData() {
        if ("DELETE".equals(eventType)) {
            return before;
        }
        return after;
    }

    /**
     * 判断是否为INSERT事件
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isInsert() {
        return "INSERT".equals(eventType);
    }

    /**
     * 判断是否为UPDATE事件
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isUpdate() {
        return "UPDATE".equals(eventType);
    }

    /**
     * 判断是否为DELETE事件
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isDelete() {
        return "DELETE".equals(eventType);
    }
}
