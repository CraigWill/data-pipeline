-- ============================================
-- 创建元数据表用于存储任务和数据源配置
-- Schema: FINANCE_USER
-- 包括：数据源表、任务配置表、运行时作业表
-- ============================================
-- 
-- 执行方式:
--   sqlplus finance_user/password@helowin @03-setup-metadata-tables.sql
-- 或以 SYSTEM 用户执行:
--   sqlplus system/helowin@helowin @03-setup-metadata-tables.sql
-- ============================================

SET ECHO ON
SET FEEDBACK ON
SET SERVEROUTPUT ON

-- ============================================
-- 1. 删除已存在的表（如果存在）
-- ============================================
PROMPT ============================================
PROMPT 1. 清理已存在的表
PROMPT ============================================

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE finance_user.runtime_jobs CASCADE CONSTRAINTS';
    DBMS_OUTPUT.PUT_LINE('✓ 删除表 runtime_jobs');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
        DBMS_OUTPUT.PUT_LINE('✓ runtime_jobs 不存在');
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE finance_user.cdc_tasks CASCADE CONSTRAINTS';
    DBMS_OUTPUT.PUT_LINE('✓ 删除表 cdc_tasks');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
        DBMS_OUTPUT.PUT_LINE('✓ cdc_tasks 不存在');
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE finance_user.cdc_datasources CASCADE CONSTRAINTS';
    DBMS_OUTPUT.PUT_LINE('✓ 删除表 cdc_datasources');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
        DBMS_OUTPUT.PUT_LINE('✓ cdc_datasources 不存在');
END;
/

-- ============================================
-- 2. 创建数据源配置表
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 2. 创建 cdc_datasources 表
PROMPT ============================================

CREATE TABLE finance_user.cdc_datasources (
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

COMMENT ON TABLE finance_user.cdc_datasources IS 'CDC数据源配置表';
COMMENT ON COLUMN finance_user.cdc_datasources.id IS '数据源唯一标识';
COMMENT ON COLUMN finance_user.cdc_datasources.name IS '数据源名称';
COMMENT ON COLUMN finance_user.cdc_datasources.host IS '数据库主机地址';
COMMENT ON COLUMN finance_user.cdc_datasources.port IS '数据库端口';
COMMENT ON COLUMN finance_user.cdc_datasources.username IS '数据库用户名';
COMMENT ON COLUMN finance_user.cdc_datasources.password IS '数据库密码';
COMMENT ON COLUMN finance_user.cdc_datasources.sid IS '数据库SID';
COMMENT ON COLUMN finance_user.cdc_datasources.description IS '数据源描述';

DBMS_OUTPUT.PUT_LINE('✓ cdc_datasources 表创建成功');

-- ============================================
-- 3. 创建 CDC 任务配置表
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 3. 创建 cdc_tasks 表
PROMPT ============================================

CREATE TABLE finance_user.cdc_tasks (
    id VARCHAR2(100) PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    datasource_id VARCHAR2(100) NOT NULL,
    schema_name VARCHAR2(100) NOT NULL,
    tables CLOB NOT NULL,
    output_path VARCHAR2(500),
    parallelism NUMBER(3) DEFAULT 4,
    split_size NUMBER(10) DEFAULT 8096,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_datasource FOREIGN KEY (datasource_id) 
        REFERENCES finance_user.cdc_datasources(id) ON DELETE CASCADE
);

COMMENT ON TABLE finance_user.cdc_tasks IS 'CDC任务配置表（静态模板，运行状态见 runtime_jobs）';
COMMENT ON COLUMN finance_user.cdc_tasks.id IS '任务唯一标识';
COMMENT ON COLUMN finance_user.cdc_tasks.name IS '任务名称';
COMMENT ON COLUMN finance_user.cdc_tasks.datasource_id IS '关联的数据源ID';
COMMENT ON COLUMN finance_user.cdc_tasks.schema_name IS 'Oracle Schema名称';
COMMENT ON COLUMN finance_user.cdc_tasks.tables IS '表名列表(JSON数组格式)';
COMMENT ON COLUMN finance_user.cdc_tasks.output_path IS '输出路径';
COMMENT ON COLUMN finance_user.cdc_tasks.parallelism IS '并行度';
COMMENT ON COLUMN finance_user.cdc_tasks.split_size IS '分片大小';

DBMS_OUTPUT.PUT_LINE('✓ cdc_tasks 表创建成功');

-- ============================================
-- 4. 创建运行时作业表
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 4. 创建 runtime_jobs 表
PROMPT ============================================

CREATE TABLE finance_user.runtime_jobs (
    id VARCHAR2(100) PRIMARY KEY,
    task_id VARCHAR2(100) NOT NULL,
    flink_job_id VARCHAR2(100),
    job_name VARCHAR2(200),
    status VARCHAR2(20) DEFAULT 'SUBMITTING',
    schema_name VARCHAR2(100),
    tables CLOB,
    parallelism NUMBER(3),
    submit_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_message VARCHAR2(2000),
    last_savepoint_path VARCHAR2(1024),
    last_savepoint_time TIMESTAMP,
    CONSTRAINT fk_runtime_task FOREIGN KEY (task_id) 
        REFERENCES finance_user.cdc_tasks(id) ON DELETE CASCADE
);

COMMENT ON TABLE finance_user.runtime_jobs IS 'Flink运行时作业跟踪表';
COMMENT ON COLUMN finance_user.runtime_jobs.id IS '运行时作业ID';
COMMENT ON COLUMN finance_user.runtime_jobs.task_id IS '关联的任务配置ID';
COMMENT ON COLUMN finance_user.runtime_jobs.flink_job_id IS 'Flink作业ID（异步获取）';
COMMENT ON COLUMN finance_user.runtime_jobs.status IS '作业状态：SUBMITTING, RUNNING, FINISHED, FAILED, CANCELED';
COMMENT ON COLUMN finance_user.runtime_jobs.tables IS '监控的表列表（JSON数组）';
COMMENT ON COLUMN finance_user.runtime_jobs.last_savepoint_path IS 'Flink 作业最后一次 savepoint 的路径';
COMMENT ON COLUMN finance_user.runtime_jobs.last_savepoint_time IS 'Flink 作业最后一次 savepoint 的时间';

DBMS_OUTPUT.PUT_LINE('✓ runtime_jobs 表创建成功');

-- ============================================
-- 5. 创建索引
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 5. 创建索引
PROMPT ============================================

CREATE INDEX idx_tasks_datasource ON finance_user.cdc_tasks(datasource_id);
CREATE INDEX idx_tasks_created ON finance_user.cdc_tasks(created_at);

CREATE INDEX idx_runtime_task ON finance_user.runtime_jobs(task_id);
CREATE INDEX idx_runtime_flink_job ON finance_user.runtime_jobs(flink_job_id);
CREATE INDEX idx_runtime_status ON finance_user.runtime_jobs(status);
CREATE INDEX idx_runtime_submit_time ON finance_user.runtime_jobs(submit_time);

DBMS_OUTPUT.PUT_LINE('✓ 索引创建成功');

-- ============================================
-- 6. 创建更新时间触发器
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 6. 创建触发器
PROMPT ============================================

CREATE OR REPLACE TRIGGER finance_user.trg_datasources_updated
BEFORE UPDATE ON finance_user.cdc_datasources
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER finance_user.trg_tasks_updated
BEFORE UPDATE ON finance_user.cdc_tasks
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

DBMS_OUTPUT.PUT_LINE('✓ 触发器创建成功');

COMMIT;

-- ============================================
-- 7. 验证表创建
-- ============================================
PROMPT
PROMPT ============================================
PROMPT 7. 验证表创建
PROMPT ============================================

SET LINESIZE 200
COL table_name FORMAT A25
COL column_count FORMAT 999

SELECT 'cdc_datasources' AS table_name, COUNT(*) AS column_count 
FROM all_tab_columns 
WHERE owner = 'FINANCE_USER' AND table_name = 'CDC_DATASOURCES'
UNION ALL
SELECT 'cdc_tasks' AS table_name, COUNT(*) AS column_count 
FROM all_tab_columns 
WHERE owner = 'FINANCE_USER' AND table_name = 'CDC_TASKS'
UNION ALL
SELECT 'runtime_jobs' AS table_name, COUNT(*) AS column_count 
FROM all_tab_columns 
WHERE owner = 'FINANCE_USER' AND table_name = 'RUNTIME_JOBS';

PROMPT
PROMPT 表详细信息:
COL owner FORMAT A15
COL num_rows FORMAT 999,999,999
SELECT owner, table_name, num_rows, tablespace_name
FROM all_tables
WHERE owner = 'FINANCE_USER'
  AND table_name IN ('CDC_DATASOURCES', 'CDC_TASKS', 'RUNTIME_JOBS')
ORDER BY table_name;

PROMPT
PROMPT ============================================
PROMPT ✅ 元数据表创建完成！
PROMPT ============================================
PROMPT 
PROMPT 已创建的表:
PROMPT   - finance_user.cdc_datasources (数据源配置)
PROMPT   - finance_user.cdc_tasks (任务配置)
PROMPT   - finance_user.runtime_jobs (运行时作业)
PROMPT 
PROMPT 下一步：
PROMPT   运行 04-check-cdc-status.sql 验证所有配置
PROMPT 
PROMPT ============================================

EXIT;
