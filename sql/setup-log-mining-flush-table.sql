-- ============================================
-- Oracle CDC Log Mining Flush Table 配置
-- ============================================
-- 此脚本用于正确配置 Flink CDC 3.x 的 log_mining_flush 表
-- 该表应该创建在 SYSTEM schema 中，而不是业务数据 schema 中

-- 连接为 SYSTEM 用户
-- sqlplus system/password@helowin

-- ============================================
-- 1. 删除 finance_user 中的旧 flush 表（如果存在）
-- ============================================
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE finance_user.log_mining_flush';
    DBMS_OUTPUT.PUT_LINE('已删除 finance_user.log_mining_flush');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN -- ORA-00942: table or view does not exist
            RAISE;
        END IF;
        DBMS_OUTPUT.PUT_LINE('finance_user.log_mining_flush 不存在，跳过删除');
END;
/

-- ============================================
-- 2. 在 SYSTEM schema 中创建 flush 表
-- ============================================
-- Flink CDC 会自动创建这个表，但我们可以预先创建以确保正确的位置
CREATE TABLE SYSTEM.log_mining_flush (
    scn NUMBER(19,0) NOT NULL,
    PRIMARY KEY (scn)
);

-- 添加注释
COMMENT ON TABLE SYSTEM.log_mining_flush IS 'Flink CDC 3.x Log Mining flush table - tracks processed SCNs';
COMMENT ON COLUMN SYSTEM.log_mining_flush.scn IS 'System Change Number (SCN) that has been flushed';

-- ============================================
-- 3. 授予 finance_user 访问权限
-- ============================================
-- finance_user 需要能够读写这个表
GRANT SELECT, INSERT, UPDATE, DELETE ON SYSTEM.log_mining_flush TO finance_user;

-- ============================================
-- 4. 验证表已创建
-- ============================================
SELECT owner, table_name, tablespace_name 
FROM dba_tables 
WHERE table_name = 'LOG_MINING_FLUSH';

-- 应该显示:
-- OWNER    TABLE_NAME          TABLESPACE_NAME
-- SYSTEM   LOG_MINING_FLUSH    SYSTEM

-- ============================================
-- 5. 验证权限
-- ============================================
SELECT grantee, privilege, table_name 
FROM dba_tab_privs 
WHERE table_name = 'LOG_MINING_FLUSH';

-- 应该显示 finance_user 有 SELECT, INSERT, UPDATE, DELETE 权限

-- ============================================
-- 注意事项
-- ============================================
-- 1. 这个表用于跟踪 Flink CDC 已处理的 SCN（System Change Number）
-- 2. 表必须在 Flink CDC 连接用户有权限访问的 schema 中
-- 3. 推荐使用 SYSTEM schema 而不是业务数据 schema
-- 4. 如果使用专用的 CDC 用户，可以创建在该用户的 schema 中
-- 5. 表结构由 Flink CDC 自动管理，不要手动修改数据

-- ============================================
-- 清理脚本（如果需要重置）
-- ============================================
-- DROP TABLE SYSTEM.log_mining_flush;
