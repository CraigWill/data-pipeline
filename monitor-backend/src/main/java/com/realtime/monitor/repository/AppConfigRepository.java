package com.realtime.monitor.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

/**
 * 应用配置 Repository
 * 
 * 从 app_config 表读取/写入系统运行时配置。
 * 配置项以 key-value 形式存储，支持动态修改无需重启。
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

    /**
     * 确保 app_config 表存在（应用启动时自动创建）
     */
    private void ensureTableExists() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_tables WHERE table_name = 'APP_CONFIG'",
                    Integer.class);
            // 表存在，尝试查询验证
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
            log.debug("APP_CONFIG 表已就绪");
        } catch (Exception e) {
            // 表不存在，创建
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE " + TABLE + " (" +
                        "  config_key   VARCHAR2(100) PRIMARY KEY," +
                        "  config_value VARCHAR2(1000) NOT NULL," +
                        "  description  VARCHAR2(500)," +
                        "  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "  updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                log.info("APP_CONFIG 表创建成功");
                insertDefaults();
            } catch (Exception createEx) {
                log.warn("APP_CONFIG 表创建失败（可能已存在）: {}", createEx.getMessage());
            }
        }
    }

    /**
     * 插入默认配置
     */
    private void insertDefaults() {
        upsert("savepoint.target.directory", "file:///opt/flink/savepoints", "Flink Savepoint 存储目录");
        upsert("checkpoint.directory", "file:///opt/flink/checkpoints", "Flink Checkpoint 存储目录");
        upsert("flink.output.path", "/opt/flink/output/cdc", "CDC 数据输出路径");
        upsert("flink.job.jar.path", "/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar", "Flink CDC 作业 JAR 路径");
        log.info("APP_CONFIG 默认配置已初始化");
    }

    /**
     * 获取配置值
     *
     * @param key          配置键
     * @param defaultValue 默认值（配置不存在时返回）
     * @return 配置值
     */
    public String getValue(String key, String defaultValue) {
        try {
            String sql = "SELECT config_value FROM " + TABLE + " WHERE config_key = ?";
            List<String> results = jdbcTemplate.queryForList(sql, String.class, key);
            if (results.isEmpty()) {
                return defaultValue;
            }
            return results.get(0);
        } catch (Exception e) {
            log.warn("读取配置失败 [{}]，使用默认值: {}", key, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 获取配置值（无默认值，不存在返回 null）
     */
    public String getValue(String key) {
        return getValue(key, null);
    }

    /**
     * 设置配置值（存在则更新，不存在则插入）
     */
    public void upsert(String key, String value, String description) {
        try {
            String sql = "MERGE INTO " + TABLE + " t " +
                    "USING (SELECT ? AS config_key FROM dual) s " +
                    "ON (t.config_key = s.config_key) " +
                    "WHEN MATCHED THEN " +
                    "  UPDATE SET config_value = ?, description = ?, updated_at = ? " +
                    "WHEN NOT MATCHED THEN " +
                    "  INSERT (config_key, config_value, description, created_at, updated_at) " +
                    "  VALUES (?, ?, ?, ?, ?)";

            Timestamp now = Timestamp.from(Instant.now());
            jdbcTemplate.update(sql,
                    key,
                    value, description, now,
                    key, value, description, now, now);
            log.debug("配置已保存: {} = {}", key, value);
        } catch (Exception e) {
            log.error("保存配置失败 [{}]: {}", key, e.getMessage());
        }
    }

    /**
     * 设置配置值（不更新描述）
     */
    public void setValue(String key, String value) {
        try {
            String sql = "MERGE INTO " + TABLE + " t " +
                    "USING (SELECT ? AS config_key FROM dual) s " +
                    "ON (t.config_key = s.config_key) " +
                    "WHEN MATCHED THEN " +
                    "  UPDATE SET config_value = ?, updated_at = ? " +
                    "WHEN NOT MATCHED THEN " +
                    "  INSERT (config_key, config_value, created_at, updated_at) " +
                    "  VALUES (?, ?, ?, ?)";

            Timestamp now = Timestamp.from(Instant.now());
            jdbcTemplate.update(sql, key, value, now, key, value, now, now);
        } catch (Exception e) {
            log.error("保存配置失败 [{}]: {}", key, e.getMessage());
        }
    }

    /**
     * 删除配置
     */
    public void delete(String key) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE config_key = ?", key);
    }

    /**
     * 获取所有配置
     */
    public Map<String, String> getAll() {
        Map<String, String> configs = new HashMap<>();
        try {
            String sql = "SELECT config_key, config_value FROM " + TABLE + " ORDER BY config_key";
            jdbcTemplate.query(sql, rs -> {
                configs.put(rs.getString("config_key"), rs.getString("config_value"));
            });
        } catch (Exception e) {
            log.warn("获取所有配置失败: {}", e.getMessage());
        }
        return configs;
    }

    /**
     * 检查配置是否存在
     */
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
}
