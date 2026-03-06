-- 检查 ACCOUNT_INFO 表状态
SET PAGESIZE 100
SET LINESIZE 200
SET FEEDBACK ON

-- 1. 检查表是否存在
PROMPT ========== 检查 ACCOUNT_INFO 表是否存在 ==========
SELECT table_name, owner, num_rows, last_analyzed 
FROM all_tables 
WHERE table_name = 'ACCOUNT_INFO' AND owner = 'FINANCE_USER';

-- 2. 检查表结构
PROMPT ========== 表结构 ==========
DESC FINANCE_USER.ACCOUNT_INFO;

-- 3. 检查补充日志
PROMPT ========== 检查补充日志配置 ==========
SELECT log_group_name, table_name, log_group_type, always, generated
FROM dba_log_groups 
WHERE owner = 'FINANCE_USER' AND table_name = 'ACCOUNT_INFO';

-- 4. 检查表数据
PROMPT ========== 表数据统计 ==========
SELECT COUNT(*) as total_rows FROM FINANCE_USER.ACCOUNT_INFO;

-- 5. 查看最近的数据
PROMPT ========== 最近的数据（前 5 条）==========
SELECT * FROM (
    SELECT ID, ACCOUNT_ID, ACCOUNT_NAME, STATUS, CREATED_TIME 
    FROM FINANCE_USER.ACCOUNT_INFO 
    ORDER BY ID DESC
) WHERE ROWNUM <= 5;

EXIT;
