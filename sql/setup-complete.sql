-- ============================================
-- 完整的数据库设置脚本
-- 包括创建用户、授权和创建表
-- ============================================

-- 1. 创建 flink_user 用户（如果不存在）
DECLARE
    user_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO user_count FROM dba_users WHERE username = 'FLINK_USER';
    IF user_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER flink_user IDENTIFIED BY password';
        EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE TO flink_user';
        EXECUTE IMMEDIATE 'GRANT CREATE TABLE, CREATE VIEW, CREATE SEQUENCE TO flink_user';
        EXECUTE IMMEDIATE 'GRANT UNLIMITED TABLESPACE TO flink_user';
        DBMS_OUTPUT.PUT_LINE('用户 flink_user 创建成功');
    ELSE
        DBMS_OUTPUT.PUT_LINE('用户 flink_user 已存在');
    END IF;
END;
/

-- 2. 删除已存在的表（如果存在）
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE flink_user.cdc_tasks CASCADE CONSTRAINTS';
    DBMS_OUTPUT.PUT_LINE('删除表 cdc_tasks');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE flink_user.cdc_datasources CASCADE CONSTRAINTS';
    DBMS_OUTPUT.PUT_LINE('删除表 cdc_datasources');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/

-- 3. 创建数据源配置表
CREATE TABLE flink_user.cdc_datasources (
    id VARCHAR2(100) PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    host VARCHAR2(200) NOT NULL,
    port NUMBER(5) NOT NULL,
    username VARCHAR2(100) NOT NULL,
    password VARCHAR2(200) NOT NULL,
    sid VARCHAR2(100) NOT NULL,
    description VARCHAR2(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. 创建 CDC 任务配置表
CREATE TABLE flink_user.cdc_tasks (
    id VARCHAR2(100) PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    datasource_id VARCHAR2(100) NOT NULL,
    schema_name VARCHAR2(100) NOT NULL,
    tables CLOB NOT NULL,
    output_path VARCHAR2(500),
    parallelism NUMBER(3) DEFAULT 4,
    split_size NUMBER(10) DEFAULT 8096,
    status VARCHAR2(20) DEFAULT 'CREATED',
    flink_job_id VARCHAR2(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_datasource FOREIGN KEY (datasource_id) 
        REFERENCES flink_user.cdc_datasources(id) ON DELETE CASCADE
);

-- 5. 创建索引
CREATE INDEX idx_tasks_datasource ON flink_user.cdc_tasks(datasource_id);
CREATE INDEX idx_tasks_status ON flink_user.cdc_tasks(status);
CREATE INDEX idx_tasks_created ON flink_user.cdc_tasks(created_at);

-- 6. 创建更新时间触发器
CREATE OR REPLACE TRIGGER flink_user.trg_datasources_updated
BEFORE UPDATE ON flink_user.cdc_datasources
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER flink_user.trg_tasks_updated
BEFORE UPDATE ON flink_user.cdc_tasks
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

-- 7. 授权给 finance_user（如果需要）
BEGIN
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.cdc_datasources TO finance_user';
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.cdc_tasks TO finance_user';
    DBMS_OUTPUT.PUT_LINE('已授权给 finance_user');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('授权失败（finance_user 可能不存在）: ' || SQLERRM);
END;
/

-- 8. 添加注释
COMMENT ON TABLE flink_user.cdc_datasources IS 'CDC数据源配置表';
COMMENT ON TABLE flink_user.cdc_tasks IS 'CDC任务配置表';

COMMIT;

-- 9. 验证表创建
SELECT 'cdc_datasources' AS table_name, COUNT(*) AS column_count 
FROM all_tab_columns 
WHERE owner = 'FLINK_USER' AND table_name = 'CDC_DATASOURCES'
UNION ALL
SELECT 'cdc_tasks' AS table_name, COUNT(*) AS column_count 
FROM all_tab_columns 
WHERE owner = 'FLINK_USER' AND table_name = 'CDC_TASKS';

PROMPT
PROMPT ============================================
PROMPT 数据库表创建完成！
PROMPT ============================================
