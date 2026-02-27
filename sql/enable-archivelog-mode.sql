-- ============================================
-- 启用 Oracle 归档日志模式
-- ============================================
-- 
-- 说明：
-- 1. 归档日志模式是 Oracle CDC (LogMiner) 的必需条件
-- 2. 启用归档日志需要重启数据库
-- 3. 需要以 SYSDBA 权限执行
--
-- 使用方法：
-- sqlplus / as sysdba @enable-archivelog-mode.sql
-- 或
-- sqlplus system/helowin@localhost:1521/helowin as sysdba @enable-archivelog-mode.sql
-- ============================================

SET ECHO ON
SET SERVEROUTPUT ON

-- 1. 检查当前状态
PROMPT ============================================
PROMPT 1. 检查当前归档日志状态
PROMPT ============================================
SELECT 'Current log mode: ' || log_mode FROM v$database;

-- 2. 关闭数据库
PROMPT ============================================
PROMPT 2. 关闭数据库
PROMPT ============================================
SHUTDOWN IMMEDIATE;

-- 3. 启动到 MOUNT 状态
PROMPT ============================================
PROMPT 3. 启动数据库到 MOUNT 状态
PROMPT ============================================
STARTUP MOUNT;

-- 4. 启用归档日志
PROMPT ============================================
PROMPT 4. 启用归档日志模式
PROMPT ============================================
ALTER DATABASE ARCHIVELOG;

-- 5. 打开数据库
PROMPT ============================================
PROMPT 5. 打开数据库
PROMPT ============================================
ALTER DATABASE OPEN;

-- 6. 验证归档日志状态
PROMPT ============================================
PROMPT 6. 验证归档日志状态
PROMPT ============================================
SELECT 'Archive log mode: ' || log_mode FROM v$database;

-- 7. 显示归档日志配置
PROMPT ============================================
PROMPT 7. 归档日志配置信息
PROMPT ============================================
ARCHIVE LOG LIST;

-- 8. 显示归档目标
PROMPT ============================================
PROMPT 8. 归档目标配置
PROMPT ============================================
SELECT dest_name, status, destination 
FROM v$archive_dest 
WHERE status != 'INACTIVE';

PROMPT ============================================
PROMPT 完成！归档日志模式已启用
PROMPT ============================================

EXIT;
