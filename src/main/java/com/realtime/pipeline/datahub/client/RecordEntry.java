package com.realtime.pipeline.datahub.client;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * DataHub记录条目
 * 表示要发送到DataHub的单条记录
 */
public class RecordEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String shardId;
    private Map<String, Object> data;
    private Map<String, String> attributes;

    public RecordEntry() {
        this.data = new HashMap<>();
        this.attributes = new HashMap<>();
    }

    public String getShardId() {
        return shardId;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public void addField(String key, Object value) {
        this.data.put(key, value);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String key, String value) {
        this.attributes.put(key, value);
    }
}
