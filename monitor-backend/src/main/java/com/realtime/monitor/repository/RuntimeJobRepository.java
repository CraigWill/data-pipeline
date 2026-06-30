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

    /** 新增作业记录。 */
    public void save(RuntimeJob job) {
        try {
            String tablesJson = job.getTables() != null
                ? objectMapper.writeValueAsString(job.getTables()) : "[]";

            Timestamp submitTime = parseTimestamp(job.getSubmitTime(), new Timestamp(System.currentTimeMillis()));
            Timestamp startTime  = parseTimestamp(job.getStartTime(), null);
            Timestamp endTime    = parseTimestamp(job.getEndTime(), null);
            Timestamp spTime     = parseTimestamp(job.getLastSavepointTime(), null);

            // OceanBase Oracle 驱动不支持 null ParameterMetaData，
            // 对 null 时间列使用占位 Timestamp(0) 再 UPDATE 为 NULL 的方式不可行，
            // 改用动态 SQL：只插入非 null 的列
            StringBuilder cols = new StringBuilder(
                "id, task_id, flink_job_id, job_name, status, schema_name, tables, parallelism, submit_time");
            StringBuilder vals = new StringBuilder("?, ?, ?, ?, ?, ?, ?, ?, ?");
            java.util.List<Object> params = new java.util.ArrayList<>(java.util.List.of(
                nn(job.getId()), nn(job.getTaskId()), nn(job.getFlinkJobId()),
                nn(job.getJobName()), nn(job.getStatus(), "SUBMITTING"),
                nn(job.getSchemaName()), tablesJson, job.getParallelism(), submitTime));

            if (startTime != null) { cols.append(", start_time"); vals.append(", ?"); params.add(startTime); }
            if (endTime != null)   { cols.append(", end_time");   vals.append(", ?"); params.add(endTime); }
            if (job.getErrorMessage() != null) { cols.append(", error_message"); vals.append(", ?"); params.add(job.getErrorMessage()); }
            if (job.getLastSavepointPath() != null) { cols.append(", last_savepoint_path"); vals.append(", ?"); params.add(job.getLastSavepointPath()); }
            if (spTime != null) { cols.append(", last_savepoint_time"); vals.append(", ?"); params.add(spTime); }

            String sql = "INSERT INTO " + TABLE + " (" + cols + ") VALUES (" + vals + ")";
            jdbcTemplate.update(sql, params.toArray());

            log.info("保存运行时作业: {}", job.getId());
        } catch (JsonProcessingException e) {
            log.error("序列化表列表失败", e);
            throw new RuntimeException("保存运行时作业失败", e);
        }
    }

    /** null → 空字符串 */
    private static String nn(String v) { return v != null ? v : ""; }
    private static String nn(String v, String def) { return v != null ? v : def; }

    public void updateSavepoint(String id, String savepointPath) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET last_savepoint_path=?, last_savepoint_time=? WHERE id=?",
            savepointPath, new Timestamp(System.currentTimeMillis()), id);
        log.info("更新作业 savepoint: {} -> {}", id, savepointPath);
    }

    /** 清除作业的 savepoint 记录（用于自愈：丢弃过期位点） */
    public void clearSavepoint(String id) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET last_savepoint_path=NULL, last_savepoint_time=NULL WHERE id=?",
            id);
        log.info("已清除作业 savepoint: {}", id);
    }

    public void updateFlinkJobId(String id, String flinkJobId) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET flink_job_id=?, status='RUNNING', start_time=? WHERE id=?",
            flinkJobId, new Timestamp(System.currentTimeMillis()), id);
        log.info("更新作业 Flink Job ID: {} -> {}", id, flinkJobId);
    }

    public void updateStatus(String id, String status, String errorMessage) {
        String truncatedError = errorMessage;
        if (truncatedError != null && truncatedError.length() > 4000) {
            truncatedError = truncatedError.substring(0, 4000) + "...(truncated)";
        }
        if ("RUNNING".equals(status)) {
            // 不设置 end_time（避免传 null）
            jdbcTemplate.update(
                "UPDATE " + TABLE + " SET status=?, error_message=? WHERE id=?",
                status, nn(truncatedError), id);
        } else {
            jdbcTemplate.update(
                "UPDATE " + TABLE + " SET status=?, error_message=?, end_time=? WHERE id=?",
                status, nn(truncatedError), new Timestamp(System.currentTimeMillis()), id);
        }
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
