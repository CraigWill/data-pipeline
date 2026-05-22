-- ============================================
-- 创建 CDC_ADMIN Schema（管理库）
-- 用于存储 CDC 系统管理表，与业务数据库分离
-- ============================================
-- 执行方式: sqlplus / as sysdba @05-setup-cdc-admin-schema.sql

SET SERVEROUTPUT ON;

PROMPT ==========================================
PROMPT  创建 CDC_ADMIN 用户和管理表
PROMPT ==========================================

-- 1. 创建用户（如果不存在）
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_users WHERE username = 'CDC_ADMIN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER cdc_admin IDENTIFIED BY cdc_admin123 DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
        EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE, CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO cdc_admin';
        DBMS_OUTPUT.PUT_LINE('✅ CDC_ADMIN 用户创建成功');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️ CDC_ADMIN 用户已存在');
        -- 确保密码和权限正确
        EXECUTE IMMEDIATE 'ALTER USER cdc_admin IDENTIFIED BY cdc_admin123 ACCOUNT UNLOCK';
        EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE, CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO cdc_admin';
    END IF;
END;
/

-- 2. 切换到 CDC_ADMIN 创建管理表
-- 注意：以下表在 CDC_ADMIN schema 下创建

-- 2.1 数据源配置表
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM all_tables WHERE owner = 'CDC_ADMIN' AND table_name = 'CDC_DATASOURCES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE cdc_admin.cdc_datasources (
                id VARCHAR2(100) PRIMARY KEY,
                name VARCHAR2(200) NOT NULL,
                host VARCHAR2(200) NOT NULL,
                port NUMBER(5) DEFAULT 1521,
                username VARCHAR2(100) NOT NULL,
                password VARCHAR2(500) NOT NULL,
                sid VARCHAR2(100) NOT NULL,
                description VARCHAR2(500),
                status VARCHAR2(20) DEFAULT ''UNTESTED'',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ';
        DBMS_OUTPUT.PUT_LINE('✅ CDC_DATASOURCES 表创建成功');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️ CDC_DATASOURCES 表已存在');
    END IF;
END;
/

-- 2.2 CDC 任务配置表
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM all_tables WHERE owner = 'CDC_ADMIN' AND table_name = 'CDC_TASKS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE cdc_admin.cdc_tasks (
                id VARCHAR2(100) PRIMARY KEY,
                name VARCHAR2(200) NOT NULL,
                datasource_id VARCHAR2(100),
                schema_name VARCHAR2(100) NOT NULL,
                tables CLOB NOT NULL,
                output_path VARCHAR2(500),
                parallelism NUMBER(3) DEFAULT 2,
                split_size NUMBER(10) DEFAULT 8096,
                status VARCHAR2(20) DEFAULT ''CREATED'',
                flink_job_id VARCHAR2(100),
                savepoint_path VARCHAR2(500),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ';
        DBMS_OUTPUT.PUT_LINE('✅ CDC_TASKS 表创建成功');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️ CDC_TASKS 表已存在');
    END IF;
END;
/

-- 2.3 运行时作业表
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM all_tables WHERE owner = 'CDC_ADMIN' AND table_name = 'RUNTIME_JOBS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE cdc_admin.runtime_jobs (
                id VARCHAR2(100) PRIMARY KEY,
                task_id VARCHAR2(100),
                task_name VARCHAR2(200),
                schema_name VARCHAR2(100),
                tables CLOB,
                parallelism NUMBER(3),
                flink_job_id VARCHAR2(100),
                status VARCHAR2(20) DEFAULT ''PENDING'',
                error_message VARCHAR2(4000),
                savepoint_path VARCHAR2(500),
                started_at TIMESTAMP,
                finished_at TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ';
        DBMS_OUTPUT.PUT_LINE('✅ RUNTIME_JOBS 表创建成功');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️ RUNTIME_JOBS 表已存在');
    END IF;
END;
/

-- 2.4 CDC 文件注册表
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM all_tables WHERE owner = 'CDC_ADMIN' AND table_name = 'CDC_FILES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE cdc_admin.cdc_files (
                id VARCHAR2(100) PRIMARY KEY,
                relative_path VARCHAR2(500) NOT NULL,
                file_name VARCHAR2(200) NOT NULL,
                table_name VARCHAR2(100),
                file_size NUMBER(20) DEFAULT 0,
                line_count NUMBER(20) DEFAULT 0,
                last_modified NUMBER(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ';
        DBMS_OUTPUT.PUT_LINE('✅ CDC_FILES 表创建成功');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️ CDC_FILES 表已存在');
    END IF;
END;
/

-- 2.5 应用配置表
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM all_tables WHERE owner = 'CDC_ADMIN' AND table_name = 'APP_CONFIG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE cdc_admin.app_config (
                config_key VARCHAR2(100) PRIMARY KEY,
                config_value VARCHAR2(1000) NOT NULL,
                description VARCHAR2(500),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ';
        DBMS_OUTPUT.PUT_LINE('✅ APP_CONFIG 表创建成功');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️ APP_CONFIG 表已存在');
    END IF;
END;
/

-- 3. 插入默认配置
MERGE INTO cdc_admin.app_config t
USING (SELECT 'savepoint.target.directory' AS config_key FROM dual) s
ON (t.config_key = s.config_key)
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, description)
    VALUES ('savepoint.target.directory', 'file:///opt/flink/savepoints', 'Flink Savepoint 存储目录');

MERGE INTO cdc_admin.app_config t
USING (SELECT 'checkpoint.directory' AS config_key FROM dual) s
ON (t.config_key = s.config_key)
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, description)
    VALUES ('checkpoint.directory', 'file:///opt/flink/checkpoints', 'Flink Checkpoint 存储目录');

COMMIT;

-- 4. 验证
PROMPT
PROMPT === 验证 CDC_ADMIN Schema ===
SELECT table_name FROM all_tables WHERE owner = 'CDC_ADMIN' ORDER BY table_name;

PROMPT
PROMPT ==========================================
PROMPT  CDC_ADMIN Schema 创建完成
PROMPT  连接信息:
PROMPT    用户名: cdc_admin
PROMPT    密码: cdc_admin123
PROMPT    表: CDC_DATASOURCES, CDC_TASKS, RUNTIME_JOBS, CDC_FILES, APP_CONFIG
PROMPT ==========================================
