package com.realtime.monitor.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.dto.RuntimeJob;
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
public class RuntimeJobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String TABLE = "runtime_jobs";

    private final RowMapper<RuntimeJob> rowMapper = new RowMapper<RuntimeJob>() {
        @Override
        public RuntimeJob mapRow(ResultSet rs, int rowNum) throws SQLException {
            RuntimeJob job = new RuntimeJob();
            job.setId(rs.getString("id"));
            job.setTaskId(rs.getString("task_id"));
            job.setFlinkJobId(rs.getString("flink_job_id"));
            job.setJobName(rs.getString("job_name"));
            job.setStatus(rs.getString("status"));
            job.setSchemaName(rs.getString("schema_name"));
            
            // 解析表列表
            String tablesJson = rs.getString("tables");
            if (tablesJson != null) {
                try {
                    List<String> tables = objectMapper.readValue(tablesJson, new TypeReference<List<String>>() {});
                    job.setTables(tables);
                } catch (JsonProcessingException e) {
                    log.error("解析表列表失败: {}", tablesJson, e);
                    job.setTables(List.of());
                }
            }
            
            job.setParallelism(rs.getInt("parallelism"));
            
            Timestamp submitTime = rs.getTimestamp("submit_time");
            if (submitTime != null) {
                job.setSubmitTime(submitTime.toInstant().toString());
            }
            
            Timestamp startTime = rs.getTimestamp("start_time");
            if (startTime != null) {
                job.setStartTime(startTime.toInstant().toString());
            }
            
            Timestamp endTime = rs.getTimestamp("end_time");
            if (endTime != null) {
                job.setEndTime(endTime.toInstant().toString());
            }
            
            job.setErrorMessage(rs.getString("error_message"));
            job.setLastSavepointPath(rs.getString("last_savepoint_path"));
            Timestamp savepointTime = rs.getTimestamp("last_savepoint_time");
            if (savepointTime != null) {
                job.setLastSavepointTime(savepointTime.toInstant().toString());
            }
            
            return job;
        }
    };

    public void save(RuntimeJob job) {
        try {
            String tablesJson = job.getTables() != null ? 
                    objectMapper.writeValueAsString(job.getTables()) : "[]";
            
            String sql = "INSERT INTO " + TABLE + 
                    " (id, task_id, flink_job_id, job_name, status, schema_name, tables, parallelism, " +
                    "  submit_time, start_time, end_time, error_message, last_savepoint_path, last_savepoint_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            jdbcTemplate.update(sql,
                    job.getId(),
                    job.getTaskId(),
                    job.getFlinkJobId(),
                    job.getJobName(),
                    job.getStatus(),
                    job.getSchemaName(),
                    tablesJson,
                    job.getParallelism(),
                    job.getSubmitTime() != null ? Timestamp.from(Instant.parse(job.getSubmitTime())) : Timestamp.from(Instant.now()),
                    job.getStartTime() != null ? Timestamp.from(Instant.parse(job.getStartTime())) : null,
                    job.getEndTime() != null ? Timestamp.from(Instant.parse(job.getEndTime())) : null,
                    job.getErrorMessage(),
                    job.getLastSavepointPath(),
                    job.getLastSavepointTime() != null ? Timestamp.from(Instant.parse(job.getLastSavepointTime())) : null);
            
            log.info("保存运行时作业: {}", job.getId());
        } catch (JsonProcessingException e) {
            log.error("序列化表列表失败", e);
            throw new RuntimeException("保存运行时作业失败", e);
        }
    }

    public void updateSavepoint(String id, String savepointPath) {
        String sql = "UPDATE " + TABLE + " SET last_savepoint_path = ?, last_savepoint_time = ? WHERE id = ?";
        jdbcTemplate.update(sql, savepointPath, Timestamp.from(Instant.now()), id);
        log.info("更新作业 savepoint: {} -> {}", id, savepointPath);
    }

    public void updateFlinkJobId(String id, String flinkJobId) {
        String sql = "UPDATE " + TABLE + " SET flink_job_id = ?, status = 'RUNNING', start_time = ? WHERE id = ?";
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(sql, flinkJobId, now, id);
        log.info("更新作业 Flink Job ID: {} -> {}", id, flinkJobId);
    }

    public void updateStatus(String id, String status, String errorMessage) {
        String sql = "UPDATE " + TABLE + " SET status = ?, error_message = ?, end_time = ? WHERE id = ?";
        Timestamp now = status.equals("RUNNING") ? null : Timestamp.from(Instant.now());
        jdbcTemplate.update(sql, status, errorMessage, now, id);
        log.info("更新作业状态: {} -> {}", id, status);
    }

    public RuntimeJob findById(String id) {
        String sql = "SELECT * FROM " + TABLE + " WHERE id = ?";
        List<RuntimeJob> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public RuntimeJob findByFlinkJobId(String flinkJobId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE flink_job_id = ?";
        List<RuntimeJob> results = jdbcTemplate.query(sql, rowMapper, flinkJobId);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<RuntimeJob> findAll() {
        String sql = "SELECT * FROM " + TABLE + " ORDER BY submit_time DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<RuntimeJob> findByStatus(String status) {
        String sql = "SELECT * FROM " + TABLE + " WHERE status = ? ORDER BY submit_time DESC";
        return jdbcTemplate.query(sql, rowMapper, status);
    }

    public List<RuntimeJob> findRunningJobs() {
        String sql = "SELECT * FROM " + TABLE + " WHERE status IN ('SUBMITTING', 'RUNNING') ORDER BY submit_time DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public void deleteById(String id) {
        String sql = "DELETE FROM " + TABLE + " WHERE id = ?";
        jdbcTemplate.update(sql, id);
        log.info("删除运行时作业: {}", id);
    }

    public void deleteByFlinkJobId(String flinkJobId) {
        String sql = "DELETE FROM " + TABLE + " WHERE flink_job_id = ?";
        jdbcTemplate.update(sql, flinkJobId);
        log.info("删除运行时作业（Flink Job ID）: {}", flinkJobId);
    }

    public boolean existsById(String id) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    /**
     * 查找包含指定表的运行中作业
     */
    public List<RuntimeJob> findRunningJobsWithTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        
        // 查询所有运行中的作业，然后在内存中过滤
        List<RuntimeJob> runningJobs = findRunningJobs();
        return runningJobs.stream()
                .filter(job -> {
                    if (job.getTables() == null) return false;
                    // 检查是否有交集
                    for (String table : tables) {
                        if (job.getTables().contains(table)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
