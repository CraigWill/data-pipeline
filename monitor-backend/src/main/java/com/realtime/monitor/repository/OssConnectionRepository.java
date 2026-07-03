package com.realtime.monitor.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.realtime.monitor.dto.OssConnection;

import lombok.extern.slf4j.Slf4j;

/**
 * OSS 连接配置 Repository（数据库无关实现）。启动时自动建表。
 */
@Slf4j
@Repository
public class OssConnectionRepository {

    private static final String TABLE = "oss_connections";
    private final JdbcTemplate jdbcTemplate;

    public OssConnectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureTable();
    }

    /** 启动时确保表存在（不存在则创建）。 */
    private void ensureTable() {
        try {
            jdbcTemplate.queryForList("SELECT id FROM " + TABLE + " WHERE 1=0");
        } catch (Exception notExist) {
            try {
                jdbcTemplate.execute(
                    "CREATE TABLE " + TABLE + " (" +
                    "  id VARCHAR2(100) PRIMARY KEY," +
                    "  name VARCHAR2(200)," +
                    "  endpoint VARCHAR2(500)," +
                    "  access_key_id VARCHAR2(200)," +
                    "  access_key_secret VARCHAR2(500)," +
                    "  bucket_name VARCHAR2(200)," +
                    "  prefix VARCHAR2(200)," +
                    "  status VARCHAR2(20) DEFAULT 'UNTESTED'," +
                    "  created_at TIMESTAMP," +
                    "  updated_at TIMESTAMP" +
                    ")");
                log.info("已创建 {} 表", TABLE);
            } catch (Exception createEx) {
                log.warn("创建 {} 表失败（可能已存在）: {}", TABLE, createEx.getMessage());
            }
        }
    }

    private final RowMapper<OssConnection> rowMapper = (ResultSet rs, int rowNum) -> {
        OssConnection c = new OssConnection();
        c.setId(rs.getString("id"));
        c.setName(rs.getString("name"));
        c.setEndpoint(rs.getString("endpoint"));
        c.setAccessKeyId(rs.getString("access_key_id"));
        c.setAccessKeySecret(rs.getString("access_key_secret"));
        c.setBucketName(rs.getString("bucket_name"));
        c.setPrefix(rs.getString("prefix"));
        try { c.setStatus(rs.getString("status")); } catch (SQLException e) { c.setStatus("UNTESTED"); }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) c.setCreatedAt(createdAt.toInstant().toString());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) c.setUpdatedAt(updatedAt.toInstant().toString());
        return c;
    };

    public void save(OssConnection c) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (existsById(c.getId())) {
            jdbcTemplate.update(
                "UPDATE " + TABLE +
                " SET name=?, endpoint=?, access_key_id=?, access_key_secret=?, bucket_name=?," +
                "     prefix=?, status=?, updated_at=? WHERE id=?",
                nn(c.getName()), nn(c.getEndpoint()), nn(c.getAccessKeyId()), nn(c.getAccessKeySecret()),
                nn(c.getBucketName()), nn(c.getPrefix()), nn(c.getStatus(), "UNTESTED"), now, c.getId());
        } else {
            jdbcTemplate.update(
                "INSERT INTO " + TABLE +
                " (id, name, endpoint, access_key_id, access_key_secret, bucket_name, prefix, status, created_at, updated_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                c.getId(), nn(c.getName()), nn(c.getEndpoint()), nn(c.getAccessKeyId()), nn(c.getAccessKeySecret()),
                nn(c.getBucketName()), nn(c.getPrefix()), nn(c.getStatus(), "UNTESTED"), now, now);
        }
        log.info("保存 OSS 连接配置: {}", c.getId());
    }

    public void updateStatus(String id, String status) {
        jdbcTemplate.update("UPDATE " + TABLE + " SET status=?, updated_at=? WHERE id=?",
            status, new Timestamp(System.currentTimeMillis()), id);
    }

    public OssConnection findById(String id) {
        List<OssConnection> r = jdbcTemplate.query("SELECT * FROM " + TABLE + " WHERE id = ?", rowMapper, id);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<OssConnection> findAll() {
        return jdbcTemplate.query("SELECT * FROM " + TABLE + " ORDER BY created_at DESC", rowMapper);
    }

    public boolean existsById(String id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = ?", id);
        log.info("删除 OSS 连接配置: {}", id);
    }

    private static String nn(String v) { return v != null ? v : ""; }
    private static String nn(String v, String d) { return v != null ? v : d; }
}
