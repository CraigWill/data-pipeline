package com.realtime.monitor.repository;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

/**
 * 应用配置 Repository
 *
 * <p>表结构：id(PK) + config_key + config_value + description + created_at + updated_at
 * <p>config_key 为普通字段（非 PK），通过 config_key 做业务查询。
 */
@Slf4j
@Repository
public class AppConfigRepository {

    private static final String TABLE = "app_config";

    private final JdbcTemplate jdbcTemplate;

    public AppConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        migrateSchema();
        insertDefaults();
    }

    // ── 表结构迁移 ────────────────────────────────────────────────

    /**
     * 确保表存在且有 id 列。根据数据库类型使用不同 DDL。
     */
    private void migrateSchema() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
            // 表存在，检查是否有 id 列
            try {
                jdbcTemplate.queryForList("SELECT id FROM " + TABLE + " WHERE 1=0");
                log.debug("APP_CONFIG 表已就绪（含 id 列）");
            } catch (Exception e) {
                // id 列不存在，添加
                try {
                    jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD id VARCHAR2(100)");
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT config_key FROM " + TABLE + " WHERE id IS NULL");
                    for (Map<String, Object> row : rows) {
                        String key = (String) row.get("config_key");
                        jdbcTemplate.update("UPDATE " + TABLE + " SET id=? WHERE config_key=?",
                            generateId(), key);
                    }
                    log.info("已为 APP_CONFIG 表添加 id 字段并填充数据");
                } catch (Exception addEx) {
                    log.debug("id 字段添加失败: {}", addEx.getMessage());
                }
            }
        } catch (Exception e) {
            // 表不存在，创建（Oracle 兼容语法，OceanBase Oracle 和原生 Oracle 都支持）
            try {
                jdbcTemplate.execute(
                    "CREATE TABLE " + TABLE + " (" +
                    "  id           VARCHAR2(100) NOT NULL," +
                    "  config_key   VARCHAR2(100) NOT NULL," +
                    "  config_value VARCHAR2(1000) NOT NULL," +
                    "  description  VARCHAR2(500)," +
                    "  created_at   TIMESTAMP," +
                    "  updated_at   TIMESTAMP," +
                    "  CONSTRAINT pk_app_config PRIMARY KEY (id)" +
                    ")");
                log.info("APP_CONFIG 表创建成功");
            } catch (Exception createEx) {
                log.warn("APP_CONFIG 表创建失败: {}", createEx.getMessage());
            }
        }
    }

    private void insertDefaults() {
        // Savepoint/Checkpoint 目录跟随环境变量（FLINK_SAVEPOINT_DIR / FLINK_CHECKPOINT_DIR），
        // 以便通过环境变量一处切换本地 file:// 与阿里云 oss:// 存储。
        String savepointDir = envOrDefault("FLINK_SAVEPOINT_DIR", "file:///opt/flink/savepoints");
        String checkpointDir = envOrDefault("FLINK_CHECKPOINT_DIR", "file:///opt/flink/checkpoints");
        upsert("savepoint.target.directory", savepointDir, "Flink Savepoint 存储目录");
        upsert("checkpoint.directory",       checkpointDir, "Flink Checkpoint 存储目录");
        upsert("flink.output.path",          "/opt/flink/output/cdc",         "CDC 数据输出路径");
        upsert("flink.job.jar.path",         "/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar", "Flink CDC 作业 JAR 路径");
        log.info("APP_CONFIG 默认配置已初始化 (savepointDir={}, checkpointDir={})", savepointDir, checkpointDir);
    }

    private static String envOrDefault(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : defaultValue;
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

    // ── 写入 ─────────────────────────────────────────────────────

    /**
     * 按 config_key 查找，存在则更新，不存在则插入（自动生成 id）。
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
                String id = generateId();
                jdbcTemplate.update(
                    "INSERT INTO " + TABLE +
                    " (id, config_key, config_value, description, created_at, updated_at)" +
                    " VALUES (?, ?, ?, ?, ?, ?)",
                    id, key, value, desc, now, now);
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
                String id = generateId();
                jdbcTemplate.update(
                    "INSERT INTO " + TABLE +
                    " (id, config_key, config_value, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    id, key, value, now, now);
            }
        } catch (Exception e) {
            log.error("保存配置失败 [{}]: {}", key, e.getMessage());
        }
    }

    public void delete(String key) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE config_key = ?", key);
    }

    // ── 工具 ──────────────────────────────────────────────────────

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
