package com.realtime.pipeline.datahub;

/**
 * DataHub发送异常
 * 当发送数据到DataHub失败时抛出此异常
 */
public class DataHubSendException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     * @param message 错误消息
     */
    public DataHubSendException(String message) {
        super(message);
    }

    /**
     * 构造函数
     * @param message 错误消息
     * @param cause 原因
     */
    public DataHubSendException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数
     * @param cause 原因
     */
    public DataHubSendException(Throwable cause) {
        super(cause);
    }
}
