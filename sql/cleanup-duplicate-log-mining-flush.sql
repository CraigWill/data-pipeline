-- ============================================
-- 清理重复的 LOG_MINING_FLUSH 表
-- 只保留 flink_user 下的表
-- ============================================

SET SERVEROUTPUT ON

DECLARE
    v_count NUMBER;
BEGIN
    -- 检查是否存在 finance_user.LOG_MINING_FLUSH
    SELECT COUNT(*) INTO v_count
    FROM dba_tables
    WHERE owner = 'FINANCE_USER'
      AND table_name = 'LOG_MINING_FLUSH';
    
    IF v_count > 0 THEN
        DBMS_OUTPUT.PUT_LINE('发现 finance_user.LOG_MINING_FLUSH 表，正在删除...');
        EXECUTE IMMEDIATE 'DROP TABLE finance_user.LOG_MINING_FLUSH CASCADE CONSTRAINTS';
        DBMS_OUTPUT.PUT_LINE('✓ 已删除 finance_user.LOG_MINING_FLUSH');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ finance_user 下没有 LOG_MINING_FLUSH 表');
    END IF;
    
    -- 验证 flink_user.LOG_MINING_FLUSH 存在
    SELECT COUNT(*) INTO v_count
    FROM dba_tables
    WHERE owner = 'FLINK_USER'
      AND table_name = 'LOG_MINING_FLUSH';
    
    IF v_count = 0 THEN
        DBMS_OUTPUT.PUT_LINE('✗ 警告: flink_user.LOG_MINING_FLUSH 表不存在！');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ flink_user.LOG_MINING_FLUSH 表存在');
    END IF;
    
    -- 验证权限
    SELECT COUNT(*) INTO v_count
    FROM dba_tab_privs
    WHERE owner = 'FLINK_USER'
      AND table_name = 'LOG_MINING_FLUSH'
      AND grantee = 'FINANCE_USER'
      AND privilege IN ('SELECT', 'INSERT', 'UPDATE', 'DELETE');
    
    IF v_count < 4 THEN
        DBMS_OUTPUT.PUT_LINE('✗ 警告: finance_user 权限不完整，正在授权...');
        EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.log_mining_flush TO finance_user';
        DBMS_OUTPUT.PUT_LINE('✓ 已授权 finance_user');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ finance_user 权限正确');
    END IF;
    
    COMMIT;
END;
/

-- 显示当前状态
PROMPT
PROMPT ============================================
PROMPT 当前 LOG_MINING_FLUSH 表状态:
PROMPT ============================================

SET LINESIZE 200
COLUMN owner FORMAT A20
COLUMN table_name FORMAT A30
COLUMN num_rows FORMAT 999999999

SELECT owner, table_name, num_rows
FROM dba_tables
WHERE table_name = 'LOG_MINING_FLUSH'
ORDER BY owner;

PROMPT
PROMPT ============================================
PROMPT finance_user 权限:
PROMPT ============================================

COLUMN grantee FORMAT A20
COLUMN privilege FORMAT A20
COLUMN grantable FORMAT A3

SELECT grantee, privilege, grantable
FROM dba_tab_privs
WHERE owner = 'FLINK_USER'
  AND table_name = 'LOG_MINING_FLUSH'
  AND grantee = 'FINANCE_USER'
ORDER BY privilege;

PROMPT
PROMPT ============================================
PROMPT 清理完成！
PROMPT ============================================
