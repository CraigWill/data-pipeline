-- ============================================
-- 创建 flink_user Schema 和 log_mining_flush 表
-- ============================================
-- 此脚本用于创建专门的 flink_user 用户和 schema
-- 用于存放 Flink CDC 的管理表（如 log_mining_flush）

-- 连接为 SYSTEM 用户
-- sqlplus system/password@helowin

-- ============================================
-- 1. 创建 flink_user 用户（如果不存在）
-- ============================================
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_users WHERE username = 'FLINK_USER';
    
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER flink_user IDENTIFIED BY flink_password';
        DBMS_OUTPUT.PUT_LINE('✓ 已创建 flink_user 用户');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ flink_user 用户已存在');
    END IF;
END;
/

-- ============================================
-- 2. 授予基本权限
-- ============================================
GRANT CONNECT TO flink_user;
GRANT RESOURCE TO flink_user;
GRANT CREATE SESSION TO flink_user;
GRANT CREATE TABLE TO flink_user;
GRANT UNLIMITED TABLESPACE TO flink_user;

-- ============================================
-- 3. 删除旧的 log_mining_flush 表
-- ============================================
-- 从 finance_user 删除
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE finance_user.log_mining_flush';
    DBMS_OUTPUT.PUT_LINE('✓ 已删除 finance_user.log_mining_flush');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN -- ORA-00942: table or view does not exist
            RAISE;
        END IF;
        DBMS_OUTPUT.PUT_LINE('✓ finance_user.log_mining_flush 不存在');
END;
/

-- 从 SYSTEM 删除（如果之前创建过）
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE SYSTEM.log_mining_flush';
    DBMS_OUTPUT.PUT_LINE('✓ 已删除 SYSTEM.log_mining_flush');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
        DBMS_OUTPUT.PUT_LINE('✓ SYSTEM.log_mining_flush 不存在');
END;
/

-- ============================================
-- 4. 在 flink_user schema 中创建 log_mining_flush 表
-- ============================================
-- 切换到 flink_user
CONNECT flink_user/flink_password@helowin

-- 创建表
CREATE TABLE flink_user.log_mining_flush (
    scn NUMBER(19,0) NOT NULL,
    PRIMARY KEY (scn)
);

-- 添加注释
COMMENT ON TABLE flink_user.log_mining_flush IS 'Flink CDC 3.x Log Mining flush table - tracks processed SCNs';
COMMENT ON COLUMN flink_user.log_mining_flush.scn IS 'System Change Number (SCN) that has been flushed';

-- ============================================
-- 5. 授予 finance_user 访问权限
-- ============================================
-- 切换回 SYSTEM
CONNECT system/password@helowin

-- finance_user 需要能够读写这个表
GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.log_mining_flush TO finance_user;

-- ============================================
-- 6. 验证配置
-- ============================================
SET SERVEROUTPUT ON

PROMPT
PROMPT ========================================
PROMPT 验证配置
PROMPT ========================================

PROMPT
PROMPT 用户信息:
SELECT username, account_status, created 
FROM dba_users 
WHERE username = 'FLINK_USER';

PROMPT
PROMPT 表位置:
SELECT owner, table_name, tablespace_name 
FROM dba_tables 
WHERE table_name = 'LOG_MINING_FLUSH';

PROMPT
PROMPT 权限:
SELECT grantee, privilege, table_name 
FROM dba_tab_privs 
WHERE table_name = 'LOG_MINING_FLUSH'
ORDER BY grantee, privilege;

PROMPT
PROMPT ========================================
PROMPT ✓ 配置完成
PROMPT ========================================

-- ============================================
-- 注意事项
-- ============================================
-- 1. flink_user 是专门用于 Flink CDC 管理表的用户
-- 2. log_mining_flush 表用于跟踪已处理的 SCN
-- 3. finance_user 需要有权限访问这个表
-- 4. 表结构由 Flink CDC 自动管理，不要手动修改数据
-- 5. 如果需要重置，可以删除表中的数据: TRUNCATE TABLE flink_user.log_mining_flush;

-- ============================================
-- 清理脚本（如果需要完全删除）
-- ============================================
-- DROP TABLE flink_user.log_mining_flush;
-- DROP USER flink_user CASCADE;
