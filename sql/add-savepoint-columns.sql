-- ============================================
-- 给 runtime_jobs 表添加 savepoint 相关列
-- ============================================
-- 
-- 说明：
-- 此脚本为 runtime_jobs 表添加 savepoint 跟踪功能
-- 用于记录 Flink 作业的 savepoint 路径和时间
--
-- 使用方法：
-- sqlplus finance_user/password@helowin @add-savepoint-columns.sql
-- 或
-- sqlplus system/helowin@helowin @add-savepoint-columns.sql
-- ============================================

SET ECHO ON
SET SERVEROUTPUT ON

PROMPT ============================================
PROMPT 添加 savepoint 相关列
PROMPT ============================================

-- 检查列是否已存在
DECLARE
    v_count NUMBER;
BEGIN
    -- 检查 last_savepoint_path 列
    SELECT COUNT(*) INTO v_count
    FROM all_tab_columns
    WHERE owner = 'FINANCE_USER'
      AND table_name = 'RUNTIME_JOBS'
      AND column_name = 'LAST_SAVEPOINT_PATH';
    
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE finance_user.runtime_jobs ADD (last_savepoint_path VARCHAR2(1024))';
        DBMS_OUTPUT.PUT_LINE('✓ 已添加 last_savepoint_path 列');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ last_savepoint_path 列已存在');
    END IF;
    
    -- 检查 last_savepoint_time 列
    SELECT COUNT(*) INTO v_count
    FROM all_tab_columns
    WHERE owner = 'FINANCE_USER'
      AND table_name = 'RUNTIME_JOBS'
      AND column_name = 'LAST_SAVEPOINT_TIME';
    
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE finance_user.runtime_jobs ADD (last_savepoint_time TIMESTAMP)';
        DBMS_OUTPUT.PUT_LINE('✓ 已添加 last_savepoint_time 列');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ last_savepoint_time 列已存在');
    END IF;
END;
/

-- 添加列注释
COMMENT ON COLUMN finance_user.runtime_jobs.last_savepoint_path IS 'Flink 作业最后一次 savepoint 的路径';
COMMENT ON COLUMN finance_user.runtime_jobs.last_savepoint_time IS 'Flink 作业最后一次 savepoint 的时间';

-- 验证列已添加
PROMPT
PROMPT ============================================
PROMPT 验证列已添加
PROMPT ============================================

SET LINESIZE 200
COL column_name FORMAT A25
COL data_type FORMAT A20
COL nullable FORMAT A8

SELECT column_name, data_type, nullable
FROM all_tab_columns
WHERE owner = 'FINANCE_USER'
  AND table_name = 'RUNTIME_JOBS'
  AND column_name IN ('LAST_SAVEPOINT_PATH', 'LAST_SAVEPOINT_TIME')
ORDER BY column_name;

PROMPT
PROMPT ============================================
PROMPT ✅ Savepoint 列添加完成
PROMPT ============================================

EXIT;
