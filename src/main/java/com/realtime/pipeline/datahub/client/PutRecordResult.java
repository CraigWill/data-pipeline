package com.realtime.pipeline.datahub.client;

import java.io.Serializable;

/**
 * 单条记录发送结果
 */
public class PutRecordResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private boolean success;
    private String recordId;
    private String errorMessage;

    public PutRecordResult() {
    }

    public PutRecordResult(boolean success, String recordId) {
        this.success = success;
        this.recordId = recordId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
