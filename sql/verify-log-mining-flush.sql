-- ============================================
-- 验证 LOG_MINING_FLUSH 表配置
-- ============================================

SET LINESIZE 200
SET PAGESIZE 50
COLUMN owner FORMAT A20
COLUMN table_name FORMAT A30
COLUMN num_rows FORMAT 999,999,999

PROMPT ========================================
PROMPT 检查 LOG_MINING_FLUSH 表位置
PROMPT ========================================
PROMPT

SELECT owner, table_name, num_rows
FROM dba_tables
WHERE table_name = 'LOG_MINING_FLUSH'
ORDER BY owner;

PROMPT
PROMPT ========================================
PROMPT 预期结果：只有 FLINK_USER 下有此表
PROMPT ========================================
PROMPT

-- 检查 finance_user 是否有权限访问 flink_user.log_mining_flush
PROMPT 检查 finance_user 对 flink_user.log_mining_flush 的权限:
SELECT grantee, privilege, grantable
FROM dba_tab_privs
WHERE owner = 'FLINK_USER'
  AND table_name = 'LOG_MINING_FLUSH'
  AND grantee = 'FINANCE_USER';

PROMPT
PROMPT 如果没有权限，需要执行以下授权:
PROMPT GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.log_mining_flush TO finance_user;
PROMPT

EXIT;
