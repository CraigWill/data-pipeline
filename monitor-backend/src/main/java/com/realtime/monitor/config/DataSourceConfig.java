package com.realtime.monitor.config;

import com.realtime.monitor.util.EnvironmentPasswordUtil;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源配置
 * 支持从环境变量读取加密的数据库密码
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        // 从环境变量读取数据库配置
        String url = buildJdbcUrl();
        String username = System.getenv().getOrDefault("DATABASE_USERNAME", "finance_user");
        
        // 使用 EnvironmentPasswordUtil 自动解密密码（必须设置，无默认值）
        String password = EnvironmentPasswordUtil.getPasswordRequired("DATABASE_PASSWORD");
        
        log.info("配置数据源:");
        log.info("  URL: {}", url);
        log.info("  Username: {}", username);
        log.info("  Password: {}", password != null && !password.isEmpty() ? "***" : "(empty)");
        
        // 创建数据源
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
     * 构建 JDBC URL
     */
    private String buildJdbcUrl() {
        String host = System.getenv().getOrDefault("DATABASE_HOST", "localhost");
        String port = System.getenv().getOrDefault("DATABASE_PORT", "1521");
        String sid = System.getenv().getOrDefault("DATABASE_SID", "helowin");
        
        return String.format("jdbc:oracle:thin:@%s:%s:%s", host, port, sid);
    }
}
