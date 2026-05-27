package com.realtime.monitor.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.dto.RuntimeJob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Flink 运行时作业 Repository（数据库无关实现）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RuntimeJobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String TABLE = "runtime_jobs";

    // ── RowMapper（两参数，匹配 RowMapper<T> 函数式接口）────────────

    private RuntimeJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        RuntimeJob job = new RuntimeJob();
        job.setId(rs.getString("id"));
        job.setTaskId(rs.getString("task_id"));
        job.setFlinkJobId(rs.getString("flink_job_id"));
        job.setJobName(rs.getString("job_name"));
        job.setStatus(rs.getString("status"));
        job.setSchemaName(rs.getString("schema_name"));

        String tablesJson = rs.getString("tables");
        if (tablesJson != null) {
            try {
                job.setTables(objectMapper.readValue(tablesJson, new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                log.error("解析表列表失败: {}", tablesJson, e);
                job.setTables(List.of());
            }
        }

        job.setParallelism(rs.getInt("parallelism"));

        Timestamp submitTime = rs.getTimestamp("submit_time");
        if (submitTime != null) job.setSubmitTime(submitTime.toInstant().toString());

        Timestamp startTime = rs.getTimestamp("start_time");
        if (startTime != null) job.setStartTime(startTime.toInstant().toString());

        Timestamp endTime = rs.getTimestamp("end_time");
        if (endTime != null) job.setEndTime(endTime.toInstant().toString());

        job.setErrorMessage(rs.getString("error_message"));
        job.setLastSavepointPath(rs.getString("last_savepoint_path"));

        Timestamp savepointTime = rs.getTimestamp("last_savepoint_time");
        if (savepointTime != null) job.setLastSavepointTime(savepointTime.toInstant().toString());

        return job;
    }

    // ── 写入 ──────────────────────────────────────────────────────

    /** 新增作业记录（runtime_jobs 只 INSERT，不 UPDATE）。 */
    public void save(RuntimeJob job) {
        try {
            String tablesJson = job.getTables() != null
                ? objectMapper.writeValueAsString(job.getTables()) : "[]";

            jdbcTemplate.update(
                "INSERT INTO " + TABLE +
                " (id, task_id, flink_job_id, job_name, status, schema_name, tables, parallelism," +
                "  submit_time, start_time, end_time, error_message, last_savepoint_path, last_savepoint_time)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                job.getId(),
                job.getTaskId(),
                job.getFlinkJobId(),
                job.getJobName(),
                job.getStatus(),
                job.getSchemaName(),
                tablesJson,
                job.getParallelism(),
                parseTimestamp(job.getSubmitTime(), new Timestamp(System.currentTimeMillis())),
                parseTimestamp(job.getStartTime(), null),
                parseTimestamp(job.getEndTime(), null),
                job.getErrorMessage(),
                job.getLastSavepointPath(),
                parseTimestamp(job.getLastSavepointTime(), null));

            log.info("保存运行时作业: {}", job.getId());
        } catch (JsonProcessingException e) {
            log.error("序列化表列表失败", e);
            throw new RuntimeException("保存运行时作业失败", e);
        }
    }

    public void updateSavepoint(String id, String savepointPath) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET last_savepoint_path=?, last_savepoint_time=? WHERE id=?",
            savepointPath, new Timestamp(System.currentTimeMillis()), id);
        log.info("更新作业 savepoint: {} -> {}", id, savepointPath);
    }

    public void updateFlinkJobId(String id, String flinkJobId) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET flink_job_id=?, status='RUNNING', start_time=? WHERE id=?",
            flinkJobId, new Timestamp(System.currentTimeMillis()), id);
        log.info("更新作业 Flink Job ID: {} -> {}", id, flinkJobId);
    }

    public void updateStatus(String id, String status, String errorMessage) {
        // 截断过长的错误信息（保守截断到 4000 字符，兼容各数据库）
        String truncatedError = errorMessage;
        if (truncatedError != null && truncatedError.length() > 4000) {
            truncatedError = truncatedError.substring(0, 4000) + "...(truncated)";
        }
        Timestamp endTime = "RUNNING".equals(status) ? null : new Timestamp(System.currentTimeMillis());
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET status=?, error_message=?, end_time=? WHERE id=?",
            status, truncatedError, endTime, id);
        log.info("更新作业状态: {} -> {}", id, status);
    }

    // ── 查询 ──────────────────────────────────────────────────────

    public RuntimeJob findById(String id) {
        List<RuntimeJob> results = jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE id = ?", this::mapRow, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public RuntimeJob findByFlinkJobId(String flinkJobId) {
        List<RuntimeJob> results = jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE flink_job_id = ?", this::mapRow, flinkJobId);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<RuntimeJob> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " ORDER BY submit_time DESC", this::mapRow);
    }

    public List<RuntimeJob> findByStatus(String status) {
        return jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE status = ? ORDER BY submit_time DESC",
            this::mapRow, status);
    }

    public List<RuntimeJob> findRunningJobs() {
        return jdbcTemplate.query(
            "SELECT * FROM " + TABLE +
            " WHERE status IN ('SUBMITTING', 'RUNNING') ORDER BY submit_time DESC",
            this::mapRow);
    }

    public List<RuntimeJob> findRunningJobsWithTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) return List.of();
        return findRunningJobs().stream()
            .filter(job -> job.getTables() != null
                && tables.stream().anyMatch(t -> job.getTables().contains(t)))
            .collect(Collectors.toList());
    }

    public boolean existsById(String id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    // ── 删除 ──────────────────────────────────────────────────────

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = ?", id);
        log.info("删除运行时作业: {}", id);
    }

    public void deleteByFlinkJobId(String flinkJobId) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE flink_job_id = ?", flinkJobId);
        log.info("删除运行时作业（Flink Job ID）: {}", flinkJobId);
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    /** 将 ISO-8601 字符串解析为 Timestamp，解析失败返回 fallback。 */
    private static Timestamp parseTimestamp(String isoStr, Timestamp fallback) {
        if (isoStr == null || isoStr.isBlank()) return fallback;
        try {
            return new Timestamp(java.time.Instant.parse(isoStr).toEpochMilli());
        } catch (Exception e) {
            return fallback;
        }
    }
}
