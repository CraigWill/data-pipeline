-- ============================================================
-- 在 FINANCE_USER 下创建 CDC 基础设施表
-- 
-- 执行方式: 以 sysdba 身份执行
--   sqlplus "/ as sysdba" @02-setup-cdc-metadata.sql
-- ============================================================

SET ECHO ON
SET FEEDBACK ON
SET SERVEROUTPUT ON

-- ============================================
-- 1. 在 FINANCE_USER schema 下创建 LOG_MINING_FLUSH 表
-- ============================================
PROMPT ============================================
PROMPT 1. 创建 LOG_MINING_FLUSH 表
PROMPT ============================================

-- Debezium/Flink CDC 需要这个表来 flush redo log buffer
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tables WHERE owner = 'FINANCE_USER' AND table_name = 'LOG_MINING_FLUSH';
    IF v_count = 0 THEN
        -- 创建 Debezium 期望的表结构
        EXECUTE IMMEDIATE 'CREATE TABLE FINANCE_USER.LOG_MINING_FLUSH (last_scn NUMBER(19,0) DEFAULT 0 NOT NULL)';
        EXECUTE IMMEDIATE 'INSERT INTO FINANCE_USER.LOG_MINING_FLUSH VALUES (0)';
        COMMIT;
        EXECUTE IMMEDIATE 'COMMENT ON TABLE FINANCE_USER.LOG_MINING_FLUSH IS ''Debezium LogMiner flush table - tracks last processed SCN''';
        EXECUTE IMMEDIATE 'COMMENT ON COLUMN FINANCE_USER.LOG_MINING_FLUSH.last_scn IS ''Last System Change Number (SCN) that has been flushed''';
        DBMS_OUTPUT.PUT_LINE('✓ FINANCE_USER.LOG_MINING_FLUSH created.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ FINANCE_USER.LOG_MINING_FLUSH already exists.');
    END IF;
END;
/

-- ============================================
-- 2. 清理其他 schema 中的 LOG_MINING_FLUSH
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 2. 清理重复的 LOG_MINING_FLUSH 表
PROMPT ============================================

-- 清理 SYSTEM 用户下的 LOG_MINING_FLUSH
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tables WHERE owner = 'SYSTEM' AND table_name = 'LOG_MINING_FLUSH';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE SYSTEM.LOG_MINING_FLUSH PURGE';
        DBMS_OUTPUT.PUT_LINE('✓ SYSTEM.LOG_MINING_FLUSH dropped.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ No LOG_MINING_FLUSH in SYSTEM schema.');
    END IF;
END;
/

-- 清理 FLINK_USER 下的 LOG_MINING_FLUSH（如果存在）
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tables WHERE owner = 'FLINK_USER' AND table_name = 'LOG_MINING_FLUSH';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE FLINK_USER.LOG_MINING_FLUSH PURGE';
        DBMS_OUTPUT.PUT_LINE('✓ FLINK_USER.LOG_MINING_FLUSH dropped.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ No LOG_MINING_FLUSH in FLINK_USER schema.');
    END IF;
END;
/

-- ============================================
-- 3. 验证配置
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 3. 验证配置
PROMPT ============================================

COL owner FORMAT A15
COL table_name FORMAT A25
COL tablespace_name FORMAT A15

PROMPT
PROMPT FINANCE_USER 所有表:
SELECT owner, table_name, tablespace_name 
FROM dba_tables 
WHERE owner = 'FINANCE_USER' 
ORDER BY table_name;

PROMPT
PROMPT LOG_MINING_FLUSH 表位置（应该只在 FINANCE_USER）:
SELECT owner, table_name, tablespace_name 
FROM dba_tables 
WHERE table_name = 'LOG_MINING_FLUSH' 
ORDER BY owner;

PROMPT
PROMPT ============================================
PROMPT ✅ CDC 基础设施配置完成！
PROMPT ============================================
PROMPT 
PROMPT 配置信息:
PROMPT   - LOG_MINING_FLUSH 位置: FINANCE_USER schema
PROMPT   - 表空间: TRANS_TBS (FINANCE_USER 默认表空间)
PROMPT 
PROMPT 下一步：
PROMPT   运行 03-setup-metadata-tables.sql 创建元数据表
PROMPT 
PROMPT ============================================

EXIT;
