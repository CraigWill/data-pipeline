-- Oracle CDC 配置脚本（简化版）
-- 请以 SYSDBA 或具有足够权限的用户执行此脚本

-- ============================================
-- 步骤 1: 检查当前配置
-- ============================================
PROMPT ========================================
PROMPT 检查当前配置
PROMPT ========================================

PROMPT 
PROMPT 1. 归档日志模式:
SELECT LOG_MODE FROM V$DATABASE;

PROMPT 
PROMPT 2. 补充日志状态:
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL FROM V$DATABASE;

-- ============================================
-- 步骤 2: 启用补充日志（可以在线执行）
-- ============================================
PROMPT 
PROMPT ========================================
PROMPT 启用补充日志
PROMPT ========================================

-- 启用最小补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;

-- 启用全列补充日志（推荐）
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

PROMPT ✅ 补充日志已启用

-- ============================================
-- 步骤 3: 验证配置
-- ============================================
PROMPT 
PROMPT ========================================
PROMPT 验证配置
PROMPT ========================================

PROMPT 
PROMPT 补充日志状态:
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL FROM V$DATABASE;

PROMPT 
PROMPT 归档日志模式:
SELECT LOG_MODE FROM V$DATABASE;

-- ============================================
-- 步骤 4: 授予权限（如果需要）
-- ============================================
PROMPT 
PROMPT ========================================
PROMPT 授予 CDC 用户权限
PROMPT ========================================

-- 如果使用 system 用户，这些权限通常已经具备
-- 如果使用其他用户，请取消下面的注释并替换 cdc_user

-- GRANT SELECT ANY TABLE TO cdc_user;
-- GRANT EXECUTE_CATALOG_ROLE TO cdc_user;
-- GRANT SELECT ANY TRANSACTION TO cdc_user;
-- GRANT LOGMINING TO cdc_user;

PROMPT 
PROMPT ========================================
PROMPT 配置完成！
PROMPT ========================================
PROMPT 
PROMPT 注意：如果归档日志模式显示为 NOARCHIVELOG，
PROMPT 需要重启数据库来启用归档日志：
PROMPT 
PROMPT   SHUTDOWN IMMEDIATE;
PROMPT   STARTUP MOUNT;
PROMPT   ALTER DATABASE ARCHIVELOG;
PROMPT   ALTER DATABASE OPEN;
PROMPT 
PROMPT ========================================

EXIT;
