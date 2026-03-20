-- 检查 LogMiner 状态和配置
-- 运行方式: sqlplus finance_user/password@helowin @check-logminer-status.sql

SET LINESIZE 200
SET PAGESIZE 100

PROMPT ========================================
PROMPT 1. 检查归档日志模式
PROMPT ========================================
SELECT log_mode, supplemental_log_data_min, force_logging 
FROM v$database;

PROMPT
PROMPT ========================================
PROMPT 2. 检查补充日志级别
PROMPT ========================================
SELECT supplemental_log_data_pk, supplemental_log_data_ui, 
       supplemental_log_data_fk, supplemental_log_data_all
FROM v$database;

PROMPT
PROMPT ========================================
PROMPT 3. 检查当前用户权限
PROMPT ========================================
SELECT * FROM session_privs 
WHERE privilege LIKE '%MINING%' 
   OR privilege LIKE '%LOGMINER%'
   OR privilege = 'SELECT ANY TRANSACTION'
   OR privilege = 'FLASHBACK ANY TABLE'
   OR privilege = 'SELECT ANY TABLE'
   OR privilege = 'LOCK ANY TABLE'
   OR privilege = 'CREATE SESSION'
   OR privilege = 'EXECUTE_CATALOG_ROLE'
   OR privilege = 'SELECT_CATALOG_ROLE';

PROMPT
PROMPT ========================================
PROMPT 4. 检查用户角色
PROMPT ========================================
SELECT * FROM session_roles;

PROMPT
PROMPT ========================================
PROMPT 5. 检查 LogMiner 字典状态
PROMPT ========================================
SELECT * FROM v$logmnr_dictionary_load;

PROMPT
PROMPT ========================================
PROMPT 6. 检查当前 Redo 日志
PROMPT ========================================
SELECT group#, thread#, sequence#, bytes/1024/1024 as size_mb, 
       members, status, archived
FROM v$log
ORDER BY group#;

PROMPT
PROMPT ========================================
PROMPT 7. 检查归档日志
PROMPT ========================================
SELECT name, sequence#, first_time, next_time, 
       blocks*block_size/1024/1024 as size_mb
FROM v$archived_log
WHERE first_time > SYSDATE - 1
ORDER BY first_time DESC;

PROMPT
PROMPT ========================================
PROMPT 8. 检查表的补充日志
PROMPT ========================================
SELECT owner, table_name, log_group_name, log_group_type
FROM dba_log_groups
WHERE owner = 'FINANCE_USER'
  AND table_name IN ('TRANS_INFO', 'ACCOUNT_INFO', 'CLM_HISTORY');

PROMPT
PROMPT ========================================
PROMPT 9. 检查 LogMiner 会话（如果有）
PROMPT ========================================
SELECT session_id, session_name, db_id, start_scn, end_scn, 
       spill_scn, spill_time, status
FROM v$logmnr_session;

PROMPT
PROMPT ========================================
PROMPT 10. 检查 LogMiner 内容（如果有活动会话）
PROMPT ========================================
SELECT COUNT(*) as logminer_records
FROM v$logmnr_contents
WHERE ROWNUM <= 10;

PROMPT
PROMPT ========================================
PROMPT 11. 检查 SCN 信息
PROMPT ========================================
SELECT current_scn, 
       TO_CHAR(scn_to_timestamp(current_scn), 'YYYY-MM-DD HH24:MI:SS') as scn_time
FROM v$database;

PROMPT
PROMPT ========================================
PROMPT 12. 检查数据库连接
PROMPT ========================================
SELECT username, osuser, machine, program, status, 
       logon_time, last_call_et
FROM v$session
WHERE username = 'FINANCE_USER'
ORDER BY logon_time DESC;

EXIT;
