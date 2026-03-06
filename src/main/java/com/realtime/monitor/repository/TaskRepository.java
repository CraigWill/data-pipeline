package com.realtime.monitor.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.dto.TaskConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TaskRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String TABLE = "cdc_tasks";

    private final RowMapper<TaskConfig> rowMapper = new RowMapper<TaskConfig>() {
        @Override
        public TaskConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            TaskConfig config = new TaskConfig();
            config.setId(rs.getString("id"));
            config.setName(rs.getString("name"));
            config.setDatasourceId(rs.getString("datasource_id"));
            config.setSchema(rs.getString("schema_name"));
            
            // Parse JSON array for tables
            String tablesJson = rs.getString("tables");
            try {
                List<String> tables = objectMapper.readValue(tablesJson, new TypeReference<List<String>>() {});
                config.setTables(tables);
            } catch (JsonProcessingException e) {
                log.error("解析表列表失败: {}", tablesJson, e);
                config.setTables(List.of());
            }
            
            config.setOutputPath(rs.getString("output_path"));
            config.setParallelism(rs.getInt("parallelism"));
            config.setSplitSize(rs.getInt("split_size"));
            config.setStatus(rs.getString("status"));
            config.setFlinkJobId(rs.getString("flink_job_id"));
            
            // 读取创建时间
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                String createdStr = createdAt.toInstant().toString();
                config.setCreated(createdStr);
                log.debug("任务 {} 创建时间: {}", config.getId(), createdStr);
            } else {
                log.warn("任务 {} 的 created_at 为 null", config.getId());
            }
            
            return config;
        }
    };

    public void save(TaskConfig config) {
        try {
            String tablesJson = objectMapper.writeValueAsString(config.getTables());
            
            String sql = "MERGE INTO " + TABLE + " t " +
                    "USING (SELECT ? AS id FROM dual) s " +
                    "ON (t.id = s.id) " +
                    "WHEN MATCHED THEN " +
                    "  UPDATE SET name=?, datasource_id=?, schema_name=?, tables=?, output_path=?, " +
                    "             parallelism=?, split_size=?, status=?, flink_job_id=?, updated_at=? " +
                    "WHEN NOT MATCHED THEN " +
                    "  INSERT (id, name, datasource_id, schema_name, tables, output_path, parallelism, " +
                    "          split_size, status, flink_job_id, created_at, updated_at) " +
                    "  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            Timestamp now = Timestamp.from(Instant.now());
            jdbcTemplate.update(sql,
                    config.getId(),
                    config.getName(), config.getDatasourceId(), config.getSchema(), tablesJson,
                    config.getOutputPath(), config.getParallelism(), config.getSplitSize(),
                    config.getStatus(), config.getFlinkJobId(), now,
                    config.getId(), config.getName(), config.getDatasourceId(), config.getSchema(),
                    tablesJson, config.getOutputPath(), config.getParallelism(), config.getSplitSize(),
                    config.getStatus(), config.getFlinkJobId(), now, now);
            
            log.info("保存任务配置: {}", config.getId());
        } catch (JsonProcessingException e) {
            log.error("序列化表列表失败", e);
            throw new RuntimeException("保存任务配置失败", e);
        }
    }

    public TaskConfig findById(String id) {
        String sql = "SELECT * FROM " + TABLE + " WHERE id = ?";
        List<TaskConfig> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<TaskConfig> findAll() {
        String sql = "SELECT * FROM " + TABLE + " ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<TaskConfig> findByStatus(String status) {
        String sql = "SELECT * FROM " + TABLE + " WHERE status = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, status);
    }

    public void updateStatus(String id, String status, String flinkJobId) {
        String sql = "UPDATE " + TABLE + " SET status = ?, flink_job_id = ?, updated_at = ? WHERE id = ?";
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(sql, status, flinkJobId, now, id);
        log.info("更新任务状态: {} -> {}", id, status);
    }

    public void deleteById(String id) {
        String sql = "DELETE FROM " + TABLE + " WHERE id = ?";
        jdbcTemplate.update(sql, id);
        log.info("删除任务配置: {}", id);
    }

    public boolean existsById(String id) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
}
