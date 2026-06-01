package com.realtime.monitor.dto;

import lombok.Data;

/**
 * 数据源配置
 */
@Data
public class DataSourceConfig {
    private String id;
    private String name;
    /** 数据库类型：ORACLE（默认）/ MYSQL / OCEANBASE / POSTGRES */
    private String type = "ORACLE";
    private String host;
    private int port = 1521;
    private String username;
    private String password;
    /** Oracle: SID；MySQL/OceanBase/Postgres: 数据库名 */
    private String sid;
    private String description;
    private String status = "UNTESTED"; // UNTESTED, SUCCESS, FAILED
    private String createdAt;
    private String updatedAt;
}
