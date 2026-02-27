-- ============================================
-- Oracle 归档日志配置脚本
-- 用于启用 LogMiner CDC
-- ============================================

-- 步骤 1: 检查当前状态
PROMPT ========================================
PROMPT 当前数据库状态
PROMPT ========================================
SELECT LOG_MODE FROM V$DATABASE;

-- 步骤 2: 启用归档日志（需要重启数据库）
PROMPT 
PROMPT ========================================
PROMPT 启用归档日志
PROMPT ========================================
PROMPT 注意：以下操作需要重启数据库
PROMPT 

-- 关闭数据库
SHUTDOWN IMMEDIATE;

-- 启动到 mount 状态
STARTUP MOUNT;

-- 启用归档日志
ALTER DATABASE ARCHIVELOG;

-- 打开数据库
ALTER DATABASE OPEN;

-- 步骤 3: 启用补充日志
PROMPT 
PROMPT ========================================
PROMPT 启用补充日志
PROMPT ========================================

ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 步骤 4: 验证配置
PROMPT 
PROMPT ========================================
PROMPT 验证配置
PROMPT ========================================

PROMPT 归档日志模式:
SELECT LOG_MODE FROM V$DATABASE;

PROMPT 
PROMPT 补充日志状态:
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL FROM V$DATABASE;

PROMPT 
PROMPT ========================================
PROMPT 配置完成！
PROMPT ========================================
PROMPT 
PROMPT 现在可以重启 Oracle CDC 作业：
PROMPT   ./submit-oracle-cdc.sh
PROMPT 
PROMPT ========================================

EXIT;
