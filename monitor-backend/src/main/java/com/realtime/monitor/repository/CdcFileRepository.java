package com.realtime.monitor.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CDC 文件映射表 — 将文件 ID 映射到实际文件路径。
 * 前端只看到 fileId，永远不接触真实路径。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CdcFileRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String TABLE = "cdc_files";

    /**
     * 注册文件并返回 ID。如果路径已存在则返回已有 ID。
     */
    public String registerFile(String filePath, String fileName, String tableName,
                               long fileSize, long lineCount, long lastModified) {
        // 先查是否已注册
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT id FROM " + TABLE + " WHERE file_path = ?", filePath);
        if (!existing.isEmpty()) {
            String existingId = (String) existing.get(0).get("id");
            // 更新统计信息（文件可能增长）
            jdbcTemplate.update(
                "UPDATE " + TABLE + " SET file_size=?, line_count=?, last_modified=? WHERE id=?",
                fileSize, lineCount, new Timestamp(lastModified), existingId);
            return existingId;
        }

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        jdbcTemplate.update(
            "INSERT INTO " + TABLE + " (id, file_path, file_name, table_name, file_size, line_count, last_modified, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            id, filePath, fileName, tableName, fileSize, lineCount,
            new Timestamp(lastModified), Timestamp.from(Instant.now()));
        return id;
    }

    /**
     * 根据 ID 获取文件路径。返回 null 表示 ID 无效。
     */
    public String getFilePath(String fileId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT file_path FROM " + TABLE + " WHERE id = ?", fileId);
        return rows.isEmpty() ? null : (String) rows.get(0).get("file_path");
    }
}
