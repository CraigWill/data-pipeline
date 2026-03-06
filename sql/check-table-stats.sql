-- ============================================
-- 检查和修复表统计信息
-- ============================================
-- 此脚本用于检查 Oracle 表的统计信息是否准确
-- 并更新过期的统计信息

-- 连接为 SYSTEM 用户
-- sqlplus system/password@helowin

SET SERVEROUTPUT ON
SET LINESIZE 200
SET PAGESIZE 100

PROMPT ========================================
PROMPT 检查 FINANCE_USER Schema 的表
PROMPT ========================================

-- 1. 查看所有表及其统计信息
PROMPT
PROMPT 1. 表列表及统计信息:
SELECT 
    table_name,
    num_rows,
    last_analyzed,
    CASE 
        WHEN last_analyzed IS NULL THEN '从未分析'
        WHEN last_analyzed < SYSDATE - 7 THEN '统计信息过期'
        ELSE '统计信息正常'
    END AS status
FROM all_tables
WHERE owner = 'FINANCE_USER'
AND table_name NOT LIKE 'BIN$%'
AND table_name NOT LIKE '%$%'
AND temporary = 'N'
ORDER BY table_name;

-- 2. 检查是否有重复的表名
PROMPT
PROMPT 2. 检查重复表名:
SELECT table_name, COUNT(*) as count
FROM all_tables
WHERE owner = 'FINANCE_USER'
AND table_name NOT LIKE 'BIN$%'
GROUP BY table_name
HAVING COUNT(*) > 1;

-- 3. 查看表的实际行数（前几个表）
PROMPT
PROMPT 3. IDS_ACCOUNT_INFO 实际行数:
SELECT COUNT(*) as actual_rows FROM finance_user.ids_account_info;

PROMPT
PROMPT 4. IDS_TRANS_INFO 实际行数:
SELECT COUNT(*) as actual_rows FROM finance_user.ids_trans_info;

-- 5. 更新表统计信息
PROMPT
PROMPT ========================================
PROMPT 更新表统计信息
PROMPT ========================================

BEGIN
    -- 更新 IDS_ACCOUNT_INFO 统计信息
    DBMS_STATS.GATHER_TABLE_STATS(
        ownname => 'FINANCE_USER',
        tabname => 'IDS_ACCOUNT_INFO',
        estimate_percent => DBMS_STATS.AUTO_SAMPLE_SIZE,
        method_opt => 'FOR ALL COLUMNS SIZE AUTO',
        cascade => TRUE
    );
    DBMS_OUTPUT.PUT_LINE('✓ 已更新 IDS_ACCOUNT_INFO 统计信息');
    
    -- 更新 IDS_TRANS_INFO 统计信息
    DBMS_STATS.GATHER_TABLE_STATS(
        ownname => 'FINANCE_USER',
        tabname => 'IDS_TRANS_INFO',
        estimate_percent => DBMS_STATS.AUTO_SAMPLE_SIZE,
        method_opt => 'FOR ALL COLUMNS SIZE AUTO',
        cascade => TRUE
    );
    DBMS_OUTPUT.PUT_LINE('✓ 已更新 IDS_TRANS_INFO 统计信息');
END;
/

-- 6. 验证更新后的统计信息
PROMPT
PROMPT ========================================
PROMPT 验证更新后的统计信息
PROMPT ========================================

SELECT 
    table_name,
    num_rows,
    last_analyzed,
    'OK' as status
FROM all_tables
WHERE owner = 'FINANCE_USER'
AND table_name IN ('IDS_ACCOUNT_INFO', 'IDS_TRANS_INFO')
ORDER BY table_name;

-- 7. 查看表的列信息
PROMPT
PROMPT ========================================
PROMPT 表列信息
PROMPT ========================================

PROMPT
PROMPT IDS_ACCOUNT_INFO 列:
SELECT column_name, data_type, data_length, nullable
FROM all_tab_columns
WHERE owner = 'FINANCE_USER'
AND table_name = 'IDS_ACCOUNT_INFO'
ORDER BY column_id;

PROMPT
PROMPT IDS_TRANS_INFO 列:
SELECT column_name, data_type, data_length, nullable
FROM all_tab_columns
WHERE owner = 'FINANCE_USER'
AND table_name = 'IDS_TRANS_INFO'
ORDER BY column_id;

-- 8. 检查回收站中的表
PROMPT
PROMPT ========================================
PROMPT 检查回收站
PROMPT ========================================

SELECT object_name, original_name, type, droptime
FROM dba_recyclebin
WHERE owner = 'FINANCE_USER'
ORDER BY droptime DESC;

-- 9. 清空回收站（可选）
-- PROMPT
-- PROMPT 清空回收站...
-- PURGE RECYCLEBIN;

PROMPT
PROMPT ========================================
PROMPT 检查完成
PROMPT ========================================
