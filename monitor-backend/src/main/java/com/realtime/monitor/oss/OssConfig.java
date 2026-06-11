package com.realtime.monitor.oss;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云 OSS 配置。
 *
 * <p>通过环境变量配置：
 * <pre>
 * OSS_ENABLED=true
 * OSS_ENDPOINT=https://oss-cn-shanghai.aliyuncs.com
 * OSS_ACCESS_KEY_ID=xxx
 * OSS_ACCESS_KEY_SECRET=xxx
 * OSS_BUCKET_NAME=your-bucket
 * OSS_PREFIX=data-pipeline/
 * </pre>
 *
 * <p>当 OSS_ENABLED=false（默认）时，不创建 OSS 客户端，仅使用本地文件存储。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "oss")
public class OssConfig {

    /** 是否启用 OSS 同步存储 */
    private boolean enabled = false;

    /** OSS Endpoint */
    private String endpoint = "https://oss-cn-shanghai.aliyuncs.com";

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** 对象键前缀（如 data-pipeline/） */
    private String prefix = "data-pipeline/";

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        if (!enabled || accessKeyId == null || accessKeySecret == null) {
            log.info("OSS 未启用或未配置 AccessKey，使用纯本地文件存储");
            return null;
        }
        log.info("初始化 OSS 客户端: endpoint={}, bucket={}, prefix={}", endpoint, bucketName, prefix);
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
