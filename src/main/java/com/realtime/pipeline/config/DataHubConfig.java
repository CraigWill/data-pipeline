package com.realtime.pipeline.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DataHub配置
 * 用于配置阿里云DataHub连接参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataHubConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * DataHub服务端点
     */
    @JsonProperty("endpoint")
    private String endpoint;

    /**
     * 访问ID
     */
    @JsonProperty("accessId")
    private String accessId;

    /**
     * 访问密钥
     */
    @JsonProperty("accessKey")
    private String accessKey;

    /**
     * 项目名称
     */
    @JsonProperty("project")
    private String project;

    /**
     * 主题名称
     */
    @JsonProperty("topic")
    private String topic;

    /**
     * 消费者组名称
     */
    @JsonProperty("consumerGroup")
    private String consumerGroup;

    /**
     * 起始消费位置: EARLIEST, LATEST, TIMESTAMP
     */
    @JsonProperty("startPosition")
    @Builder.Default
    private String startPosition = "LATEST";

    /**
     * 最大重试次数，默认3次
     */
    @JsonProperty("maxRetries")
    @Builder.Default
    private int maxRetries = 3;

    /**
     * 重试间隔（秒），默认2秒
     */
    @JsonProperty("retryBackoff")
    @Builder.Default
    private int retryBackoff = 2;

    /**
     * 验证配置的有效性
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("DataHub endpoint is required");
        }
        if (accessId == null || accessId.trim().isEmpty()) {
            throw new IllegalArgumentException("DataHub accessId is required");
        }
        if (accessKey == null || accessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("DataHub accessKey is required");
        }
        if (project == null || project.trim().isEmpty()) {
            throw new IllegalArgumentException("DataHub project is required");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("DataHub topic is required");
        }
        if (consumerGroup == null || consumerGroup.trim().isEmpty()) {
            throw new IllegalArgumentException("DataHub consumerGroup is required");
        }
        if (!startPosition.matches("EARLIEST|LATEST|TIMESTAMP")) {
            throw new IllegalArgumentException("DataHub startPosition must be EARLIEST, LATEST, or TIMESTAMP");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("DataHub maxRetries must be non-negative");
        }
        if (retryBackoff <= 0) {
            throw new IllegalArgumentException("DataHub retryBackoff must be positive");
        }
    }
}
