-- ============================================
-- 修复 LOG_MINING_FLUSH 表结构
-- ============================================
-- 
-- 说明：
-- Debezium/Flink CDC 需要特定的表结构
-- 删除手动创建的表，让 Debezium 自动创建
--
-- 使用方法：
-- sqlplus system/helowin@helowin @fix-log-mining-flush-table.sql
-- ============================================

SET ECHO ON
SET SERVEROUTPUT ON

PROMPT ============================================
PROMPT 修复 LOG_MINING_FLUSH 表
PROMPT ============================================

-- 删除现有的表
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM all_tables
    WHERE owner = 'FINANCE_USER'
      AND table_name = 'LOG_MINING_FLUSH';
    
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE finance_user.log_mining_flush PURGE';
        DBMS_OUTPUT.PUT_LINE('✓ 已删除旧的 LOG_MINING_FLUSH 表');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ LOG_MINING_FLUSH 表不存在');
    END IF;
END;
/

-- 创建正确结构的表（Debezium 期望的结构）
-- 参考: https://debezium.io/documentation/reference/stable/connectors/oracle.html
CREATE TABLE finance_user.log_mining_flush (
    last_scn NUMBER(19,0) DEFAULT 0 NOT NULL
);

-- 插入初始记录
INSERT INTO finance_user.log_mining_flush VALUES (0);
COMMIT;

-- 添加注释
COMMENT ON TABLE finance_user.log_mining_flush IS 'Debezium LogMiner flush table - tracks last processed SCN';
COMMENT ON COLUMN finance_user.log_mining_flush.last_scn IS 'Last System Change Number (SCN) that has been flushed';

-- 验证表结构
PROMPT
PROMPT ============================================
PROMPT 验证表结构
PROMPT ============================================

SET LINESIZE 200
COL column_name FORMAT A30
COL data_type FORMAT A20
COL nullable FORMAT A8

DESC finance_user.log_mining_flush;

PROMPT
PROMPT 表内容:
SELECT * FROM finance_user.log_mining_flush;

PROMPT
PROMPT ============================================
PROMPT ✅ LOG_MINING_FLUSH 表修复完成
PROMPT ============================================
PROMPT
PROMPT 注意：
PROMPT   - 表结构: LAST_SCN NUMBER(19,0)
PROMPT   - 初始值: 0
PROMPT   - Debezium 会自动更新此表
PROMPT
PROMPT 下一步：
PROMPT   - 重启 Flink CDC 作业
PROMPT   - 检查作业日志
PROMPT
PROMPT ============================================

EXIT;
