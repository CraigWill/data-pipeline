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
 *   <li>MYSQL            — MySQL 5.7+ / OceanBase MySQL 模式，默认端口 3306 / OB 默认 2881</li>
 *   <li>OCEANBASE_ORACLE — OceanBase Oracle 兼容模式，使用 OceanBase JDBC Driver，
 *                          用户名格式 {@code user@tenant}，默认端口 2881</li>
 *   <li>ORACLE           — Oracle 11g+，默认端口 1521，需要 SID</li>
 *   <li>POSTGRES         — PostgreSQL 12+，默认端口 5432</li>
 * </ul>
 *
 * <p>OceanBase Oracle 模式示例环境变量：
 * <pre>
 * CDC_ADMIN_TYPE=OCEANBASE_ORACLE
 * CDC_ADMIN_HOST=172.17.0.1
 * CDC_ADMIN_PORT=2881
 * CDC_ADMIN_DATABASE=cdcdb
 * CDC_ADMIN_USERNAME=cdc_admin@oracle_tenant
 * CDC_ADMIN_PASSWORD=xxx
 * </pre>
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        DbType dbType   = DbType.fromEnv();
        String url      = buildJdbcUrl(dbType);
        String username = System.getenv().getOrDefault("CDC_ADMIN_USERNAME", defaultUsername(dbType));
        String password = EnvironmentPasswordUtil.getPasswordRequired("CDC_ADMIN_PASSWORD");
        String driver   = driverClassName(dbType);

        log.info("配置 CDC 管理库数据源 [{}]:", dbType);
        log.info("  URL:      {}", url);
        log.info("  Username: {}", username);
        log.info("  Driver:   {}", driver);

        HikariDataSource ds = (HikariDataSource) DataSourceBuilder
                .create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driver)
                .build();

        // OceanBase Oracle 模式额外连接属性
        if (dbType == DbType.OCEANBASE_ORACLE) {
            ds.addDataSourceProperty("useOraclePrepareExecute", "true");
        }

        return ds;
    }

    // ── URL 构建 ──────────────────────────────────────────────────

    private String buildJdbcUrl(DbType dbType) {
        String host = System.getenv().getOrDefault("CDC_ADMIN_HOST", "localhost");
        String port = System.getenv("CDC_ADMIN_PORT");

        switch (dbType) {
            case MYSQL: {
                if (port == null) port = "3306";
                String database = System.getenv().getOrDefault("CDC_ADMIN_DATABASE", "cdc_admin");
                return String.format(
                    "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf8"
                    + "&useSSL=false&allowPublicKeyRetrieval=true",
                    host, port, database);
            }
            case OCEANBASE_ORACLE: {
                // OceanBase Oracle 模式使用 OceanBase JDBC Driver
                // useServerPrepStmts=false 避免 ParameterMetaData 为 null 的问题
                if (port == null) port = "2881";
                String database = System.getenv().getOrDefault("CDC_ADMIN_DATABASE", "cdc_admin");
                return String.format(
                    "jdbc:oceanbase://%s:%s/%s?compatibleMode=ORACLE"
                    + "&useUnicode=true&characterEncoding=utf8&useSSL=false"
                    + "&useServerPrepStmts=false&useInformationSchema=false",
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
            case MYSQL:            return "com.mysql.cj.jdbc.Driver";
            case OCEANBASE_ORACLE: return "com.alipay.oceanbase.jdbc.Driver";
            case ORACLE:           return "oracle.jdbc.OracleDriver";
            case POSTGRES:         return "org.postgresql.Driver";
            default:               throw new IllegalStateException("未处理的数据库类型: " + dbType);
        }
    }

    private String defaultUsername(DbType dbType) {
        switch (dbType) {
            case OCEANBASE_ORACLE: return "cdc_admin@oracle_tenant";
            default:               return "cdc_admin";
        }
    }
}
