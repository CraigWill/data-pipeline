-- ============================================
-- Oracle CDC 综合状态检查脚本
-- 包括：归档日志、补充日志、LogMiner、权限、表状态
-- ============================================
--
-- 使用方法：
-- sqlplus system/helowin@localhost:1521/helowin @04-check-cdc-status.sql
-- 或
-- sqlplus finance_user/password@helowin @04-check-cdc-status.sql
-- ============================================

SET PAGESIZE 100
SET LINESIZE 200
SET ECHO ON
SET SERVEROUTPUT ON
SET FEEDBACK ON

-- ============================================
-- 1. 数据库归档日志模式
-- ============================================
PROMPT ============================================
PROMPT 1. 数据库归档日志模式
PROMPT ============================================

SELECT 
    name AS database_name,
    log_mode,
    CASE 
        WHEN log_mode = 'ARCHIVELOG' THEN '✓ 已启用'
        ELSE '✗ 未启用 - 需要运行 01-enable-oracle-cdc.sql'
    END AS status
FROM v$database;

-- ============================================
-- 2. 补充日志状态
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 2. 补充日志状态
PROMPT ============================================

SELECT 
    supplemental_log_data_min AS min_supplemental,
    supplemental_log_data_all AS all_columns,
    supplemental_log_data_pk AS primary_key,
    supplemental_log_data_ui AS unique_index,
    supplemental_log_data_fk AS foreign_key,
    force_logging
FROM v$database;

PROMPT
PROMPT 预期: MIN=YES/IMPLICIT, ALL=YES

-- ============================================
-- 3. 归档日志配置
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 3. 归档日志配置
PROMPT ============================================

ARCHIVE LOG LIST;

PROMPT
PROMPT 归档目标:
SELECT 
    dest_id,
    dest_name,
    status,
    destination,
    binding
FROM v$archive_dest 
WHERE status != 'INACTIVE'
ORDER BY dest_id;

-- ============================================
-- 4. 最近的归档日志
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 4. 最近的归档日志（最近 10 条）
PROMPT ============================================

SELECT 
    sequence#,
    TO_CHAR(first_time, 'YYYY-MM-DD HH24:MI:SS') AS first_time,
    TO_CHAR(next_time, 'YYYY-MM-DD HH24:MI:SS') AS next_time,
    ROUND(blocks * block_size / 1024 / 1024, 2) AS size_mb
FROM v$archived_log 
WHERE dest_id = 1
ORDER BY sequence# DESC
FETCH FIRST 10 ROWS ONLY;

-- ============================================
-- 5. 当前 Redo Log 状态
-- ============================================
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

-- ============================================
-- 7. FINANCE_USER 权限检查
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 7. FINANCE_USER 系统权限
PROMPT ============================================

SELECT privilege 
FROM dba_sys_privs 
WHERE grantee = 'FINANCE_USER'
ORDER BY privilege;

PROMPT
PROMPT 关键权限检查:
SELECT 
    CASE WHEN COUNT(*) > 0 THEN '✓' ELSE '✗' END AS status,
    'SELECT ANY TRANSACTION' AS privilege
FROM dba_sys_privs 
WHERE grantee = 'FINANCE_USER' AND privilege = 'SELECT ANY TRANSACTION'
UNION ALL
SELECT 
    CASE WHEN COUNT(*) > 0 THEN '✓' ELSE '✗' END,
    'FLASHBACK ANY TABLE'
FROM dba_sys_privs 
WHERE grantee = 'FINANCE_USER' AND privilege = 'FLASHBACK ANY TABLE'
UNION ALL
SELECT 
    CASE WHEN COUNT(*) > 0 THEN '✓' ELSE '✗' END,
    'SELECT ANY TABLE'
FROM dba_sys_privs 
WHERE grantee = 'FINANCE_USER' AND privilege = 'SELECT ANY TABLE';

PROMPT
PROMPT FINANCE_USER 角色:
SELECT granted_role 
FROM dba_role_privs 
WHERE grantee = 'FINANCE_USER'
ORDER BY granted_role;

-- ============================================
-- 8. LOG_MINING_FLUSH 表位置
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 8. LOG_MINING_FLUSH 表位置
PROMPT ============================================

COL owner FORMAT A20
COL table_name FORMAT A30
COL tablespace_name FORMAT A15
COL num_rows FORMAT 999,999,999

SELECT owner, table_name, tablespace_name, num_rows
FROM dba_tables
WHERE table_name = 'LOG_MINING_FLUSH'
ORDER BY owner;

PROMPT
PROMPT 预期: 只有 FINANCE_USER 下有此表

-- ============================================
-- 9. CDC 元数据表检查
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 9. CDC 元数据表
PROMPT ============================================

SELECT owner, table_name, num_rows, tablespace_name
FROM dba_tables
WHERE owner = 'FINANCE_USER'
  AND table_name IN ('CDC_DATASOURCES', 'CDC_TASKS', 'RUNTIME_JOBS', 'LOG_MINING_FLUSH')
ORDER BY table_name;

-- ============================================
-- 10. 业务表补充日志检查
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 10. 业务表补充日志配置
PROMPT ============================================

SELECT owner, table_name, log_group_name, log_group_type, always
FROM dba_log_groups
WHERE owner = 'FINANCE_USER'
  AND table_name IN ('TRANS_INFO', 'ACCOUNT_INFO', 'IDS_TRANS_INFO', 'IDS_ACCOUNT_INFO')
ORDER BY table_name, log_group_name;

-- ============================================
-- 11. 业务表统计信息
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 11. 业务表统计信息
PROMPT ============================================

SELECT 
    table_name,
    num_rows,
    TO_CHAR(last_analyzed, 'YYYY-MM-DD HH24:MI:SS') AS last_analyzed,
    CASE 
        WHEN last_analyzed IS NULL THEN '⚠ 从未分析'
        WHEN last_analyzed < SYSDATE - 7 THEN '⚠ 统计信息过期'
        ELSE '✓ 正常'
    END AS status
FROM all_tables
WHERE owner = 'FINANCE_USER'
  AND table_name NOT LIKE 'BIN$%'
  AND table_name NOT LIKE '%$%'
  AND temporary = 'N'
ORDER BY table_name;

-- ============================================
-- 12. LogMiner 字典状态
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 12. LogMiner 字典状态
PROMPT ============================================

SELECT * FROM v$logmnr_dictionary_load;

-- ============================================
-- 13. 当前 SCN 信息
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 13. 当前 SCN 信息
PROMPT ============================================

SELECT 
    current_scn, 
    TO_CHAR(scn_to_timestamp(current_scn), 'YYYY-MM-DD HH24:MI:SS') as scn_time
FROM v$database;

-- ============================================
-- 14. 活动的 LogMiner 会话
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 14. 活动的 LogMiner 会话
PROMPT ============================================

SELECT session_id, session_name, db_id, start_scn, end_scn, status
FROM v$logmnr_session;

PROMPT
PROMPT 如果没有数据，说明当前没有活动的 LogMiner 会话（正常）

-- ============================================
-- 15. 数据库连接
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 15. FINANCE_USER 连接
PROMPT ============================================

COL username FORMAT A15
COL osuser FORMAT A15
COL machine FORMAT A30
COL program FORMAT A30
COL status FORMAT A10

SELECT username, osuser, machine, program, status, 
       TO_CHAR(logon_time, 'YYYY-MM-DD HH24:MI:SS') AS logon_time
FROM v$session
WHERE username = 'FINANCE_USER'
ORDER BY username, logon_time DESC;

-- ============================================
-- 总结
-- ============================================
PROMPT
PROMPT ============================================
PROMPT ✅ CDC 状态检查完成
PROMPT ============================================
PROMPT
PROMPT 检查清单:
PROMPT   □ 归档日志模式: ARCHIVELOG
PROMPT   □ 补充日志: MIN=YES, ALL=YES
PROMPT   □ FINANCE_USER 权限: SELECT ANY TRANSACTION, FLASHBACK ANY TABLE
PROMPT   □ LOG_MINING_FLUSH: 在 FINANCE_USER schema
PROMPT   □ 元数据表: CDC_DATASOURCES, CDC_TASKS, RUNTIME_JOBS
PROMPT   □ 业务表补充日志: 已配置
PROMPT
PROMPT 如果有任何 ✗ 或 ⚠ 标记，请检查相应的配置脚本
PROMPT ============================================

EXIT;
