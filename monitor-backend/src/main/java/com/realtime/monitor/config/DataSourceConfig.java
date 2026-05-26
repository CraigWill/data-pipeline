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
 * 数据源配置 — CDC 管理库（存储元数据表）
 *
 * <p>优先读取 CDC_ADMIN_* 环境变量（独立的 CDC 管理库，端口 1522）；
 * 若未设置则回退到 DATABASE_* 变量（兼容旧配置）。
 *
 * <p>业务库（finance_user / 1521）仅供 Flink CDC Job 直接使用，
 * 不作为 Spring DataSource 注入。
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        String url      = buildJdbcUrl();
        String username = System.getenv().getOrDefault("CDC_ADMIN_USERNAME", "cdc_admin");

        String password = EnvironmentPasswordUtil.getPasswordRequired("CDC_ADMIN_PASSWORD");

        log.info("配置 CDC 管理库数据源:");
        log.info("  URL: {}", url);
        log.info("  Username: {}", username);
        log.info("  Password: {}", (password != null && !password.isEmpty()) ? "***" : "(empty)");

        HikariDataSource dataSource = DataSourceBuilder
                .create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("oracle.jdbc.OracleDriver")
                .build();

        return dataSource;
    }

    /**
     * 构建 JDBC URL，只读 CDC_ADMIN_* 环境变量。
     */
    private String buildJdbcUrl() {
        String host = System.getenv().getOrDefault("CDC_ADMIN_HOST", "localhost");
        String port = System.getenv().getOrDefault("CDC_ADMIN_PORT", "1521");
        String sid  = System.getenv().getOrDefault("CDC_ADMIN_SID",  "helowin");
        return String.format("jdbc:oracle:thin:@%s:%s:%s", host, port, sid);
    }
}
