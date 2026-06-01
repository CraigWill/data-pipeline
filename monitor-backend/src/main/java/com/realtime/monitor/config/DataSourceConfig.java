package com.realtime.monitor.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.realtime.monitor.util.EnvironmentPasswordUtil;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据源配置 — CDC 管理库。
 *
 * <p>通过环境变量 {@code CDC_ADMIN_TYPE} 选择数据库类型（默认 MYSQL），
 * 动态构建 JDBC URL 和选择对应的 JDBC Driver。
 *
 * <p>支持的类型：
 * <ul>
 *   <li>MYSQL   — MySQL 5.7+ / OceanBase（MySQL 兼容模式），端口默认 3306 / OB 默认 2881</li>
 *   <li>ORACLE  — Oracle 11g+，端口默认 1521，需要 SID（CDC_ADMIN_SID）</li>
 *   <li>POSTGRES — PostgreSQL 12+，端口默认 5432</li>
 * </ul>
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        DbType dbType = DbType.fromEnv();
        String url      = buildJdbcUrl(dbType);
        String username = System.getenv().getOrDefault("CDC_ADMIN_USERNAME", defaultUsername(dbType));
        String password = EnvironmentPasswordUtil.getPasswordRequired("CDC_ADMIN_PASSWORD");
        String driver   = driverClassName(dbType);

        log.info("配置 CDC 管理库数据源 [{}]:", dbType);
        log.info("  URL:      {}", url);
        log.info("  Username: {}", username);
        log.info("  Driver:   {}", driver);

        return DataSourceBuilder
                .create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driver)
                .build();
    }

    // ── URL 构建 ──────────────────────────────────────────────────

    private String buildJdbcUrl(DbType dbType) {
        String host = System.getenv().getOrDefault("CDC_ADMIN_HOST", "localhost");
        String port = System.getenv("CDC_ADMIN_PORT");

        switch (dbType) {
            case MYSQL: {
                if (port == null) port = "3306";
                String database = System.getenv().getOrDefault("CDC_ADMIN_DATABASE", "cdc_admin");
                // useSSL=false 兼容无 SSL 环境；allowPublicKeyRetrieval 兼容 MySQL 8+
                return String.format(
                    "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf8"
                    + "&useSSL=false&allowPublicKeyRetrieval=true",
                    host, port, database);
            }
            case ORACLE: {
                if (port == null) port = "1521";
                String sid = System.getenv().getOrDefault("CDC_ADMIN_SID", "orcl");
                return String.format("jdbc:oracle:thin:@%s:%s:%s", host, port, sid);
            }
            case POSTGRES: {
                if (port == null) port = "5432";
                String database = System.getenv().getOrDefault("CDC_ADMIN_DATABASE", "cdc_admin");
                return String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
            }
            default:
                throw new IllegalStateException("未处理的数据库类型: " + dbType);
        }
    }

    // ── Driver 选择 ───────────────────────────────────────────────

    private String driverClassName(DbType dbType) {
        switch (dbType) {
            case MYSQL:    return "com.mysql.cj.jdbc.Driver";
            case ORACLE:   return "oracle.jdbc.OracleDriver";
            case POSTGRES: return "org.postgresql.Driver";
            default:       throw new IllegalStateException("未处理的数据库类型: " + dbType);
        }
    }

    private String defaultUsername(DbType dbType) {
        switch (dbType) {
            case MYSQL:    return "cdc_admin";
            case ORACLE:   return "cdc_admin";
            case POSTGRES: return "cdc_admin";
            default:       return "cdc_admin";
        }
    }
}
