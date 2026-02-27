-- Oracle CDC 配置脚本
-- 此脚本用于配置 Oracle 数据库以支持 CDC（LogMiner）
-- 需要以 SYSDBA 身份执行

-- ============================================
-- 1. 检查归档日志模式
-- ============================================
SELECT LOG_MODE FROM V$DATABASE;
-- 如果返回 NOARCHIVELOG，需要启用归档日志模式

-- ============================================
-- 2. 启用归档日志模式（如果未启用）
-- ============================================
-- 注意：这需要重启数据库
-- SHUTDOWN IMMEDIATE;
-- STARTUP MOUNT;
-- ALTER DATABASE ARCHIVELOG;
-- ALTER DATABASE OPEN;

-- ============================================
-- 3. 启用补充日志（必需）
-- ============================================
-- 启用数据库级别的补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;

-- 启用所有列的补充日志（推荐）
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 或者只为特定表启用补充日志
-- ALTER TABLE finance_user.trans_info ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- ============================================
-- 4. 验证补充日志状态
-- ============================================
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL 
FROM V$DATABASE;
-- 应该返回 YES 或 IMPLICIT

-- ============================================
-- 5. 创建 CDC 用户（可选，推荐）
-- ============================================
-- 创建专用的 CDC 用户而不是使用 SYSTEM
-- CREATE USER cdc_user IDENTIFIED BY cdc_password;

-- 授予必要的权限
-- GRANT CREATE SESSION TO cdc_user;
-- GRANT SELECT ANY TABLE TO cdc_user;
-- GRANT EXECUTE_CATALOG_ROLE TO cdc_user;
-- GRANT SELECT ANY TRANSACTION TO cdc_user;
-- GRANT LOGMINING TO cdc_user;

-- 授予对特定 schema 的访问权限
-- GRANT SELECT ON finance_user.trans_info TO cdc_user;

-- ============================================
-- 6. 配置归档日志保留策略
-- ============================================
-- 设置归档日志保留时间（例如 7 天）
-- ALTER SYSTEM SET LOG_ARCHIVE_DEST_1='LOCATION=/u01/app/oracle/oradata/archive' SCOPE=BOTH;

-- ============================================
-- 7. 验证 LogMiner 配置
-- ============================================
-- 检查 LogMiner 字典
SELECT * FROM V$LOGMNR_DICTIONARY;

-- 检查归档日志
SELECT NAME, SEQUENCE#, FIRST_TIME, NEXT_TIME 
FROM V$ARCHIVED_LOG 
ORDER BY FIRST_TIME DESC;

-- ============================================
-- 8. 测试 LogMiner
-- ============================================
-- 添加日志文件
-- EXECUTE DBMS_LOGMNR.ADD_LOGFILE(
--   LOGFILENAME => '/path/to/redo/log/file',
--   OPTIONS => DBMS_LOGMNR.NEW
-- );

-- 启动 LogMiner
-- EXECUTE DBMS_LOGMNR.START_LOGMNR(
--   OPTIONS => DBMS_LOGMNR.DICT_FROM_ONLINE_CATALOG
-- );

-- 查询变更
-- SELECT OPERATION, SQL_REDO, SQL_UNDO, TIMESTAMP
-- FROM V$LOGMNR_CONTENTS
-- WHERE SEG_OWNER = 'FINANCE_USER' AND TABLE_NAME = 'TRANS_INFO';

-- 停止 LogMiner
-- EXECUTE DBMS_LOGMNR.END_LOGMNR;

-- ============================================
-- 9. 监控和维护
-- ============================================
-- 检查归档日志空间使用
SELECT * FROM V$RECOVERY_FILE_DEST;

-- 清理旧的归档日志（根据保留策略）
-- RMAN> DELETE ARCHIVELOG ALL COMPLETED BEFORE 'SYSDATE-7';

-- ============================================
-- 注意事项
-- ============================================
-- 1. 启用归档日志会增加磁盘空间使用
-- 2. 补充日志会增加 redo log 的大小
-- 3. 定期清理归档日志以避免磁盘空间不足
-- 4. 在生产环境中，建议使用专用的 CDC 用户
-- 5. 确保有足够的磁盘空间存储归档日志
-- 6. 监控 LogMiner 性能和资源使用
