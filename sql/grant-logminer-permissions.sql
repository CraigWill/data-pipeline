-- 授予 LogMiner 所需的权限
-- 运行方式: sqlplus system/password@helowin @grant-logminer-permissions.sql

SET ECHO ON
SET FEEDBACK ON

PROMPT ========================================
PROMPT 授予 FINANCE_USER LogMiner 权限
PROMPT ========================================

-- 基本会话权限
GRANT CREATE SESSION TO finance_user;

-- LogMiner 核心权限
GRANT SELECT ANY TRANSACTION TO finance_user;
GRANT FLASHBACK ANY TABLE TO finance_user;
GRANT SELECT ANY TABLE TO finance_user;
GRANT LOCK ANY TABLE TO finance_user;

-- 执行 DBMS_LOGMNR 包的权限
GRANT EXECUTE_CATALOG_ROLE TO finance_user;
GRANT SELECT_CATALOG_ROLE TO finance_user;

-- 访问 LogMiner 视图的权限
GRANT SELECT ON v_$logmnr_contents TO finance_user;
GRANT SELECT ON v_$logmnr_logs TO finance_user;
GRANT SELECT ON v_$log TO finance_user;
GRANT SELECT ON v_$log_history TO finance_user;
GRANT SELECT ON v_$logfile TO finance_user;
GRANT SELECT ON v_$archived_log TO finance_user;
GRANT SELECT ON v_$database TO finance_user;
GRANT SELECT ON v_$thread TO finance_user;
GRANT SELECT ON v_$parameter TO finance_user;
GRANT SELECT ON v_$nls_parameters TO finance_user;
GRANT SELECT ON v_$timezone_names TO finance_user;
GRANT SELECT ON v_$transaction TO finance_user;

-- DBA 视图权限
GRANT SELECT ON dba_objects TO finance_user;
GRANT SELECT ON dba_users TO finance_user;
GRANT SELECT ON dba_tables TO finance_user;
GRANT SELECT ON dba_tab_columns TO finance_user;
GRANT SELECT ON dba_constraints TO finance_user;
GRANT SELECT ON dba_cons_columns TO finance_user;
GRANT SELECT ON dba_log_groups TO finance_user;
GRANT SELECT ON dba_log_group_columns TO finance_user;

-- 执行 DBMS_LOGMNR 包
GRANT EXECUTE ON dbms_logmnr TO finance_user;
GRANT EXECUTE ON dbms_logmnr_d TO finance_user;

PROMPT
PROMPT ========================================
PROMPT 权限授予完成
PROMPT ========================================

-- 验证权限
SELECT privilege FROM dba_sys_privs WHERE grantee = 'FINANCE_USER';
SELECT granted_role FROM dba_role_privs WHERE grantee = 'FINANCE_USER';

EXIT;
