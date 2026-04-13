-- ============================================
-- Oracle CDC 完整配置脚本
-- 包括：启用归档日志、启用补充日志、授予权限
-- ============================================
-- 
-- 说明：
-- 1. 归档日志模式是 Oracle CDC (LogMiner) 的必需条件
-- 2. 启用归档日志需要重启数据库
-- 3. 需要以 SYSDBA 权限执行
--
-- 使用方法：
-- sqlplus / as sysdba @01-enable-oracle-cdc.sql
-- 或
-- sqlplus system/helowin@localhost:1521/helowin as sysdba @01-enable-oracle-cdc.sql
-- ============================================

SET ECHO ON
SET SERVEROUTPUT ON
SET LINESIZE 200
SET PAGESIZE 100

-- ============================================
-- 步骤 1: 检查当前状态
-- ============================================
PROMPT ============================================
PROMPT 1. 检查当前数据库状态
PROMPT ============================================

PROMPT 归档日志模式:
SELECT 'Current log mode: ' || log_mode FROM v$database;

PROMPT
PROMPT 补充日志状态:
SELECT supplemental_log_data_min, supplemental_log_data_all FROM v$database;

-- ============================================
-- 步骤 2: 启用归档日志（需要重启数据库）
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 2. 启用归档日志模式
PROMPT ============================================
PROMPT 注意：以下操作需要重启数据库
PROMPT

-- 关闭数据库
PROMPT 关闭数据库...
SHUTDOWN IMMEDIATE;

-- 启动到 MOUNT 状态
PROMPT 启动数据库到 MOUNT 状态...
STARTUP MOUNT;

-- 启用归档日志
PROMPT 启用归档日志模式...
ALTER DATABASE ARCHIVELOG;

-- 打开数据库
PROMPT 打开数据库...
ALTER DATABASE OPEN;

-- 验证归档日志状态
PROMPT
PROMPT 验证归档日志状态:
SELECT 'Archive log mode: ' || log_mode FROM v$database;

-- 显示归档日志配置
PROMPT
PROMPT 归档日志配置信息:
ARCHIVE LOG LIST;

-- ============================================
-- 步骤 3: 启用补充日志（必需）
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 3. 启用补充日志
PROMPT ============================================

-- 启用最小补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;

-- 启用全列补充日志（推荐）
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

PROMPT ✅ 补充日志已启用

-- ============================================
-- 步骤 4: 授予 CDC 用户权限
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 4. 授予 FINANCE_USER LogMiner 权限
PROMPT ============================================

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

PROMPT ✅ 权限授予完成

-- ============================================
-- 步骤 5: 验证配置
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 5. 验证配置
PROMPT ============================================

PROMPT
PROMPT 归档日志模式:
SELECT log_mode FROM v$database;

PROMPT
PROMPT 补充日志状态:
SELECT supplemental_log_data_min, supplemental_log_data_all FROM v$database;

PROMPT
PROMPT 归档目标配置:
SELECT dest_name, status, destination 
FROM v$archive_dest 
WHERE status != 'INACTIVE';

PROMPT
PROMPT FINANCE_USER 系统权限:
SELECT privilege FROM dba_sys_privs WHERE grantee = 'FINANCE_USER' ORDER BY privilege;

PROMPT
PROMPT FINANCE_USER 角色:
SELECT granted_role FROM dba_role_privs WHERE grantee = 'FINANCE_USER' ORDER BY granted_role;

PROMPT
PROMPT ============================================
PROMPT ✅ Oracle CDC 配置完成！
PROMPT ============================================
PROMPT 
PROMPT 下一步：
PROMPT   1. 运行 02-setup-flink-infrastructure.sql 创建 Flink 基础设施
PROMPT   2. 运行 03-setup-metadata-tables.sql 创建元数据表
PROMPT   3. 运行 04-check-cdc-status.sql 验证配置
PROMPT 
PROMPT ============================================

EXIT;
