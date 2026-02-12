package com.realtime.pipeline.datahub.client;

import java.io.Serializable;

/**
 * DataHub主题信息
 */
public class TopicInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String projectName;
    private String topicName;
    private int shardCount;
    private int lifecycle;
    private String recordType;
    private String comment;

    public TopicInfo() {
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public int getShardCount() {
        return shardCount;
    }

    public void setShardCount(int shardCount) {
        this.shardCount = shardCount;
    }

    public int getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(int lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
