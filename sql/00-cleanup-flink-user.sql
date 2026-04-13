-- ============================================
-- 清理 FLINK_USER 和 FLINK_TBS 表空间
-- ============================================
-- 
-- 说明：
-- 此脚本用于删除之前创建的 FLINK_USER 用户和 FLINK_TBS 表空间
-- 将所有 CDC 相关表移到 FINANCE_USER 下
--
-- 使用方法：
-- sqlplus / as sysdba @00-cleanup-flink-user.sql
-- ============================================

SET ECHO ON
SET SERVEROUTPUT ON
SET LINESIZE 200

PROMPT ============================================
PROMPT 清理 FLINK_USER 和 FLINK_TBS
PROMPT ============================================

-- ============================================
-- 1. 删除 FLINK_USER 用户（级联删除所有对象）
-- ============================================
PROMPT
PROMPT 1. 删除 FLINK_USER 用户...

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_users WHERE username = 'FLINK_USER';
    
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP USER FLINK_USER CASCADE';
        DBMS_OUTPUT.PUT_LINE('✓ FLINK_USER 用户已删除');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ FLINK_USER 用户不存在');
    END IF;
END;
/

-- ============================================
-- 2. 删除 FLINK_TBS 表空间
-- ============================================
PROMPT
PROMPT 2. 删除 FLINK_TBS 表空间...

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tablespaces WHERE tablespace_name = 'FLINK_TBS';
    
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLESPACE FLINK_TBS INCLUDING CONTENTS AND DATAFILES CASCADE CONSTRAINTS';
        DBMS_OUTPUT.PUT_LINE('✓ FLINK_TBS 表空间已删除');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ FLINK_TBS 表空间不存在');
    END IF;
END;
/

-- ============================================
-- 3. 验证清理结果
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 3. 验证清理结果
PROMPT ============================================

PROMPT
PROMPT 检查 FLINK_USER 用户:
SELECT COUNT(*) AS flink_user_count FROM dba_users WHERE username = 'FLINK_USER';

PROMPT
PROMPT 检查 FLINK_TBS 表空间:
SELECT COUNT(*) AS flink_tbs_count FROM dba_tablespaces WHERE tablespace_name = 'FLINK_TBS';

PROMPT
PROMPT ============================================
PROMPT ✅ 清理完成
PROMPT ============================================
PROMPT
PROMPT 下一步：
PROMPT   运行 02-setup-cdc-metadata.sql 在 FINANCE_USER 下创建 CDC 表
PROMPT
PROMPT ============================================

EXIT;
