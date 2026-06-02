package com.realtime.monitor.config;

/**
 * 支持的 CDC Admin 数据库类型。
 *
 * <p>通过环境变量 {@code CDC_ADMIN_TYPE} 指定，默认 {@code MYSQL}。
 * 支持的值（大小写不敏感）：
 * <ul>
 *   <li>{@code MYSQL}           — MySQL 5.7+ / OceanBase（MySQL 兼容模式）</li>
 *   <li>{@code OCEANBASE_ORACLE} — OceanBase（Oracle 兼容模式），使用 OceanBase JDBC Driver</li>
 *   <li>{@code ORACLE}          — Oracle 11g+</li>
 *   <li>{@code POSTGRES}        — PostgreSQL 12+</li>
 * </ul>
 */
public enum DbType {
    MYSQL, OCEANBASE_ORACLE, ORACLE, POSTGRES;

    /** 从环境变量读取，默认 MYSQL。 */
    public static DbType fromEnv() {
        String raw = System.getenv("CDC_ADMIN_TYPE");
        if (raw == null || raw.isBlank()) return MYSQL;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "不支持的 CDC_ADMIN_TYPE: " + raw
                + "，可选值: MYSQL, OCEANBASE_ORACLE, ORACLE, POSTGRES");
        }
    }
}
