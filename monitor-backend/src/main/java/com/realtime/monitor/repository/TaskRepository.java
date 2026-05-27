package com.realtime.monitor.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.dto.TaskConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CDC 任务配置 Repository（数据库无关实现）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TaskRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String TABLE = "cdc_tasks";

    // ── RowMapper（方法引用，避免 field initializer 中 objectMapper 未初始化）──

    private TaskConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
        TaskConfig config = new TaskConfig();
        config.setId(rs.getString("id"));
        config.setName(rs.getString("name"));
        config.setDatasourceId(rs.getString("datasource_id"));
        config.setSchema(rs.getString("schema_name"));

        String tablesJson = rs.getString("tables");
        try {
            config.setTables(objectMapper.readValue(tablesJson, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            log.error("解析表列表失败: {}", tablesJson, e);
            config.setTables(List.of());
        }

        config.setOutputPath(rs.getString("output_path"));
        config.setParallelism(rs.getInt("parallelism"));
        config.setSplitSize(rs.getInt("split_size"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) config.setCreated(createdAt.toInstant().toString());
        return config;
    }

    // ── 写入（SELECT 检查后 INSERT / UPDATE）─────────────────────

    public void save(TaskConfig config) {
        try {
            String tablesJson = objectMapper.writeValueAsString(config.getTables());
            Timestamp now = new Timestamp(System.currentTimeMillis());

            if (existsById(config.getId())) {
                jdbcTemplate.update(
                    "UPDATE " + TABLE +
                    " SET name=?, datasource_id=?, schema_name=?, tables=?," +
                    "     output_path=?, parallelism=?, split_size=?, updated_at=?" +
                    " WHERE id=?",
                    config.getName(), config.getDatasourceId(), config.getSchema(), tablesJson,
                    config.getOutputPath(), config.getParallelism(), config.getSplitSize(), now,
                    config.getId());
            } else {
                jdbcTemplate.update(
                    "INSERT INTO " + TABLE +
                    " (id, name, datasource_id, schema_name, tables, output_path," +
                    "  parallelism, split_size, created_at, updated_at)" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    config.getId(), config.getName(), config.getDatasourceId(), config.getSchema(),
                    tablesJson, config.getOutputPath(), config.getParallelism(), config.getSplitSize(),
                    now, now);
            }
            log.info("任务配置已保存: {}", config.getId());
        } catch (JsonProcessingException e) {
            log.error("序列化表列表失败", e);
            throw new RuntimeException("保存任务配置失败", e);
        }
    }

    // ── 查询 ──────────────────────────────────────────────────────

    public TaskConfig findById(String id) {
        List<TaskConfig> results = jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<TaskConfig> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " ORDER BY created_at DESC", this::mapRow);
    }

    public boolean existsById(String id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    // ── 删除 ──────────────────────────────────────────────────────

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = ?", id);
        log.info("删除任务配置: {}", id);
    }
}
