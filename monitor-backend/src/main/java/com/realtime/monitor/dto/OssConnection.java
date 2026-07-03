package com.realtime.monitor.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * OSS 连接配置（可管理多个，建任务时按需选择）。
 */
@Data
public class OssConnection {
    private String id;
    private String name;
    private String endpoint;

    @JsonProperty("accessKeyId")
    @JsonAlias("access_key_id")
    private String accessKeyId;

    @JsonProperty("accessKeySecret")
    @JsonAlias("access_key_secret")
    private String accessKeySecret;

    @JsonProperty("bucketName")
    @JsonAlias("bucket_name")
    private String bucketName;

    private String prefix;
    private String status;      // UNTESTED / SUCCESS / FAILED
    private String createdAt;
    private String updatedAt;
}
