package com.realtime.monitor.repository;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

/**
 * 应用配置 Repository
 *
 * <p>从 app_config 表读写系统运行时配置，使用标准 SQL（无数据库特定语法）。
 */
@Slf4j
@Repository
public class AppConfigRepository {

    private static final String TABLE = "app_config";

    private final JdbcTemplate jdbcTemplate;

    public AppConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureTableExists();
    }

    // ── 表初始化 ──────────────────────────────────────────────────

    /**
     * 确保 app_config 表存在。
     * 使用标准 SQL：先查 information_schema，不存在则 CREATE TABLE。
     */
    private void ensureTableExists() {
        try {
            // 先尝试查询，如果表存在直接返回
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
            log.debug("APP_CONFIG 表已就绪");
        } catch (Exception e) {
            // 表不存在，创建（使用跨数据库兼容的 DDL）
            try {
                jdbcTemplate.execute(
                    "CREATE TABLE " + TABLE + " (" +
                    "  config_key   VARCHAR(100)  NOT NULL," +
                    "  config_value VARCHAR(1000) NOT NULL," +
                    "  description  VARCHAR(500)," +
                    "  created_at   TIMESTAMP," +
                    "  updated_at   TIMESTAMP," +
                    "  PRIMARY KEY (config_key)" +
                    ")");
                log.info("APP_CONFIG 表创建成功");
            } catch (Exception createEx) {
                log.warn("APP_CONFIG 表创建失败（可能已存在）: {}", createEx.getMessage());
            }
        }
        insertDefaults();
    }

    private void insertDefaults() {
        upsert("savepoint.target.directory", "file:///opt/flink/savepoints", "Flink Savepoint 存储目录");
        upsert("checkpoint.directory",       "file:///opt/flink/checkpoints", "Flink Checkpoint 存储目录");
        upsert("flink.output.path",          "/opt/flink/output/cdc",         "CDC 数据输出路径");
        upsert("flink.job.jar.path",         "/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar", "Flink CDC 作业 JAR 路径");
        log.info("APP_CONFIG 默认配置已初始化");
    }

    // ── 读取 ──────────────────────────────────────────────────────

    public String getValue(String key, String defaultValue) {
        try {
            List<String> results = jdbcTemplate.queryForList(
                "SELECT config_value FROM " + TABLE + " WHERE config_key = ?",
                String.class, key);
            return results.isEmpty() ? defaultValue : results.get(0);
        } catch (Exception e) {
            log.warn("读取配置失败 [{}]，使用默认值: {}", key, e.getMessage());
            return defaultValue;
        }
    }

    public String getValue(String key) {
        return getValue(key, null);
    }

    public Map<String, String> getAll() {
        Map<String, String> configs = new HashMap<>();
        try {
            jdbcTemplate.query(
                "SELECT config_key, config_value FROM " + TABLE + " ORDER BY config_key",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                    configs.put(rs.getString("config_key"), rs.getString("config_value")));
        } catch (Exception e) {
            log.warn("获取所有配置失败: {}", e.getMessage());
        }
        return configs;
    }

    public boolean exists(String key) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE config_key = ?",
                Integer.class, key);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ── 写入（SELECT 检查后 INSERT / UPDATE）─────────────────────

    /**
     * 存在则更新，不存在则插入（不使用任何数据库特定的 upsert 语法）。
     */
    public void upsert(String key, String value, String description) {
        try {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            String desc = description != null ? description : "";
            if (exists(key)) {
                jdbcTemplate.update(
                    "UPDATE " + TABLE +
                    " SET config_value=?, description=?, updated_at=? WHERE config_key=?",
                    value, desc, now, key);
            } else {
                jdbcTemplate.update(
                    "INSERT INTO " + TABLE +
                    " (config_key, config_value, description, created_at, updated_at)" +
                    " VALUES (?, ?, ?, ?, ?)",
                    key, value, desc, now, now);
            }
            log.debug("配置已保存: {} = {}", key, value);
        } catch (Exception e) {
            log.error("保存配置失败 [{}]: {}", key, e.getMessage());
        }
    }

    public void setValue(String key, String value) {
        try {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            if (exists(key)) {
                jdbcTemplate.update(
                    "UPDATE " + TABLE + " SET config_value=?, updated_at=? WHERE config_key=?",
                    value, now, key);
            } else {
                jdbcTemplate.update(
                    "INSERT INTO " + TABLE +
                    " (config_key, config_value, created_at, updated_at) VALUES (?, ?, ?, ?)",
                    key, value, now, now);
            }
        } catch (Exception e) {
            log.error("保存配置失败 [{}]: {}", key, e.getMessage());
        }
    }

    public void delete(String key) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE config_key = ?", key);
    }
}
