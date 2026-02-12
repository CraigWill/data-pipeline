package com.realtime.pipeline.datahub.client;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量记录发送结果
 */
public class PutRecordsResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int failedRecordCount;
    private List<FailedRecord> failedRecords;

    public PutRecordsResult() {
        this.failedRecords = new ArrayList<>();
    }

    public int getFailedRecordCount() {
        return failedRecordCount;
    }

    public void setFailedRecordCount(int failedRecordCount) {
        this.failedRecordCount = failedRecordCount;
    }

    public List<FailedRecord> getFailedRecords() {
        return failedRecords;
    }

    public void setFailedRecords(List<FailedRecord> failedRecords) {
        this.failedRecords = failedRecords;
    }

    public void addFailedRecord(FailedRecord failedRecord) {
        this.failedRecords.add(failedRecord);
        this.failedRecordCount = this.failedRecords.size();
    }

    /**
     * 失败记录信息
     */
    public static class FailedRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int index;
        private String errorCode;
        private String errorMessage;

        public FailedRecord() {
        }

        public FailedRecord(int index, String errorCode, String errorMessage) {
            this.index = index;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
