-- ============================================
-- 检查 Oracle 归档日志状态
-- ============================================
--
-- 使用方法：
-- sqlplus system/helowin@localhost:1521/helowin @check-archivelog-status.sql
-- ============================================

SET PAGESIZE 100
SET LINESIZE 200
SET ECHO ON
SET SERVEROUTPUT ON

PROMPT ============================================
PROMPT 1. 数据库归档日志模式
PROMPT ============================================
SELECT 
    name AS database_name,
    log_mode,
    CASE 
        WHEN log_mode = 'ARCHIVELOG' THEN '✓ 已启用'
        ELSE '✗ 未启用'
    END AS status
FROM v$database;

PROMPT
PROMPT ============================================
PROMPT 2. 归档日志列表
PROMPT ============================================
ARCHIVE LOG LIST;

PROMPT
PROMPT ============================================
PROMPT 3. 归档目标配置
PROMPT ============================================
SELECT 
    dest_id,
    dest_name,
    status,
    destination,
    binding
FROM v$archive_dest 
WHERE status != 'INACTIVE'
ORDER BY dest_id;

PROMPT
PROMPT ============================================
PROMPT 4. 归档日志历史（最近 10 条）
PROMPT ============================================
SELECT 
    sequence#,
    first_time,
    next_time,
    blocks,
    block_size,
    ROUND(blocks * block_size / 1024 / 1024, 2) AS size_mb
FROM v$archived_log 
WHERE dest_id = 1
ORDER BY sequence# DESC
FETCH FIRST 10 ROWS ONLY;

PROMPT
PROMPT ============================================
PROMPT 5. 当前 Redo Log 状态
PROMPT ============================================
SELECT 
    group#,
    thread#,
    sequence#,
    bytes / 1024 / 1024 AS size_mb,
    members,
    status,
    archived
FROM v$log
ORDER BY group#;

PROMPT
PROMPT ============================================
PROMPT 检查完成
PROMPT ============================================

EXIT;
