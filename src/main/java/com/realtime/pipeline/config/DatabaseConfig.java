package com.realtime.pipeline.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 数据库配置
 * 用于配置OceanBase数据库连接参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 数据库主机地址
     */
    @JsonProperty("host")
    private String host;

    /**
     * 数据库端口
     */
    @JsonProperty("port")
    private int port;

    /**
     * 数据库用户名
     */
    @JsonProperty("username")
    private String username;

    /**
     * 数据库密码
     */
    @JsonProperty("password")
    private String password;

    /**
     * 要监控的Schema
     */
    @JsonProperty("schema")
    private String schema;

    /**
     * 要监控的表列表
     */
    @JsonProperty("tables")
    private List<String> tables;

    /**
     * 连接超时时间（秒），默认30秒
     */
    @JsonProperty("connectionTimeout")
    @Builder.Default
    private int connectionTimeout = 30;

    /**
     * 自动重连间隔（秒），默认30秒
     */
    @JsonProperty("reconnectInterval")
    @Builder.Default
    private int reconnectInterval = 30;

    /**
     * 验证配置的有效性
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Database host is required");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Database port must be between 1 and 65535");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Database username is required");
        }
        if (password == null) {
            throw new IllegalArgumentException("Database password is required");
        }
        if (schema == null || schema.trim().isEmpty()) {
            throw new IllegalArgumentException("Database schema is required");
        }
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("At least one table must be specified");
        }
        if (connectionTimeout <= 0) {
            throw new IllegalArgumentException("Connection timeout must be positive");
        }
        if (reconnectInterval <= 0) {
            throw new IllegalArgumentException("Reconnect interval must be positive");
        }
    }
}
