package com.realtime.monitor.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.realtime.monitor.dto.DataSourceConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据源配置 Repository（数据库无关实现）。
 *
 * <p>表结构说明（cdc_datasources）：
 * <ul>
 *   <li>type  — 数据库类型（ORACLE / MYSQL / OCEANBASE / POSTGRES），取代已废弃的 db_type 列</li>
 *   <li>db_type — 已废弃，启动时自动删除</li>
 * </ul>
 */
@Slf4j
@Repository
public class DataSourceRepository {

    private static final String TABLE = "cdc_datasources";

    private final JdbcTemplate jdbcTemplate;

    public DataSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        migrateSchema();
    }

    // ── 表结构维护 ────────────────────────────────────────────────

    /**
     * 启动时执行表结构迁移：
     * 1. 确保 status 列存在
     * 2. 确保 type 列存在，并从旧 db_type 迁移数据
     * 3. 删除废弃的 db_type 列
     */
    private void migrateSchema() {
        ensureColumn("status", "VARCHAR2(20) DEFAULT 'UNTESTED'");
        ensureColumn("type",   "VARCHAR2(20) DEFAULT 'ORACLE'");
        migrateDbTypeToType();
        dropColumnIfExists("db_type");
    }

    /** 确保指定列存在，不存在则添加（使用 VARCHAR2 兼容 Oracle 模式）。 */
    private void ensureColumn(String column, String definition) {
        try {
            jdbcTemplate.queryForList("SELECT " + column + " FROM " + TABLE + " WHERE 1=0");
        } catch (Exception e) {
            try {
                jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD " + column + " " + definition);
                log.info("已为 {} 表添加 {} 字段", TABLE, column);
            } catch (Exception addEx) {
                log.debug("{} 字段添加失败（可能已存在）: {}", column, addEx.getMessage());
            }
        }
    }

    /** 将旧 db_type 列的数据迁移到 type 列（仅当 db_type 存在时执行）。 */
    private void migrateDbTypeToType() {
        try {
            jdbcTemplate.queryForList("SELECT db_type FROM " + TABLE + " WHERE 1=0");
            // db_type 列存在，迁移数据
            jdbcTemplate.update(
                "UPDATE " + TABLE + " SET type = db_type WHERE db_type IS NOT NULL AND (type IS NULL OR type = 'ORACLE')");
            log.info("已将 db_type 数据迁移到 type 字段");
        } catch (Exception e) {
            // db_type 列不存在，无需迁移
        }
    }

    /** 删除指定列（如果存在）。 */
    private void dropColumnIfExists(String column) {
        try {
            jdbcTemplate.queryForList("SELECT " + column + " FROM " + TABLE + " WHERE 1=0");
            // 列存在，删除
            try {
                jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP COLUMN " + column);
                log.info("已从 {} 表删除废弃字段 {}", TABLE, column);
            } catch (Exception dropEx) {
                log.warn("删除 {} 字段失败: {}", column, dropEx.getMessage());
            }
        } catch (Exception e) {
            // 列不存在，无需删除
        }
    }

    // ── RowMapper ─────────────────────────────────────────────────

    private final RowMapper<DataSourceConfig> rowMapper = (ResultSet rs, int rowNum) -> {
        DataSourceConfig config = new DataSourceConfig();
        config.setId(rs.getString("id"));
        config.setName(rs.getString("name"));
        config.setHost(rs.getString("host"));
        config.setPort(rs.getInt("port"));
        config.setUsername(rs.getString("username"));
        config.setPassword(rs.getString("password"));
        config.setSid(rs.getString("sid"));
        config.setDescription(rs.getString("description"));
        try { config.setStatus(rs.getString("status")); }
        catch (SQLException ex) { config.setStatus("UNTESTED"); }
        try {
            String type = rs.getString("type");
            config.setType(type != null && !type.isBlank() ? type : "ORACLE");
        } catch (SQLException ex) {
            config.setType("ORACLE");
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) config.setCreatedAt(createdAt.toInstant().toString());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) config.setUpdatedAt(updatedAt.toInstant().toString());
        return config;
    };

    // ── 写入（SELECT 检查后 INSERT / UPDATE）─────────────────────

    public void save(DataSourceConfig config) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String type = nn(config.getType(), "ORACLE");
        if (existsById(config.getId())) {
            jdbcTemplate.update(
                "UPDATE " + TABLE +
                " SET name=?, host=?, port=?, username=?, password=?, sid=?," +
                "     description=?, status=?, type=?, updated_at=? WHERE id=?",
                nn(config.getName()), nn(config.getHost()), config.getPort(),
                nn(config.getUsername()), nn(config.getPassword()), nn(config.getSid()),
                nn(config.getDescription()), nn(config.getStatus(), "UNTESTED"), type, now,
                config.getId());
        } else {
            jdbcTemplate.update(
                "INSERT INTO " + TABLE +
                " (id, name, host, port, username, password, sid, description, status, type, created_at, updated_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                config.getId(), nn(config.getName()), nn(config.getHost()), config.getPort(),
                nn(config.getUsername()), nn(config.getPassword()), nn(config.getSid()),
                nn(config.getDescription()), nn(config.getStatus(), "UNTESTED"), type, now, now);
        }
        log.info("保存数据源配置: {} (type={})", config.getId(), type);
    }

    /** null → 空字符串（避免 OceanBase 驱动 ParameterMetaData NPE） */
    private static String nn(String value) { return value != null ? value : ""; }
    private static String nn(String value, String defaultValue) { return value != null ? value : defaultValue; }

    public void updateStatus(String id, String status) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET status=?, updated_at=? WHERE id=?",
            status, new Timestamp(System.currentTimeMillis()), id);
        log.info("更新数据源状态: {} -> {}", id, status);
    }

    // ── 查询 ──────────────────────────────────────────────────────

    public DataSourceConfig findById(String id) {
        List<DataSourceConfig> results = jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE id = ?", rowMapper, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<DataSourceConfig> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " ORDER BY created_at DESC", rowMapper);
    }

    public boolean existsById(String id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    // ── 删除 ──────────────────────────────────────────────────────

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = ?", id);
        log.info("删除数据源配置: {}", id);
    }
}
