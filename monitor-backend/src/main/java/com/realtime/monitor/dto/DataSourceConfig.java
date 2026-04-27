package com.realtime.monitor.dto;

import lombok.Data;

/**
 * 数据源配置
 */
@Data
public class DataSourceConfig {
    private String id;
    private String name;
    private String host;
    private int port = 1521;
    private String username;
    private String password;
    private String sid;
    private String description;
    private String status = "UNTESTED"; // UNTESTED, SUCCESS, FAILED
    private String createdAt;
    private String updatedAt;
}
