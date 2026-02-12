package com.realtime.pipeline.datahub.client;

/**
 * DataHub客户端异常
 */
public class DataHubClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    private final String errorCode;
    private final int statusCode;

    public DataHubClientException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
        this.statusCode = -1;
    }

    public DataHubClientException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
        this.statusCode = -1;
    }

    public DataHubClientException(String errorCode, int statusCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
