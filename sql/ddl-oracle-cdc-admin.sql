-- ============================================================
-- CDC Admin 元数据表 DDL — Oracle 11g+
-- Schema: CDC_ADMIN
-- 提取自: oracle-cdcdb 容器 (helowin / CDC_ADMIN)
-- 包含: APP_CONFIG, CDC_DATASOURCES, CDC_FILES, CDC_TASKS, RUNTIME_JOBS
-- ============================================================
-- 执行方式:
--   sqlplus system/helowin@helowin @ddl-oracle-cdc-admin.sql
-- 或以 CDC_ADMIN 用户执行（需先创建用户并授权）:
--   sqlplus cdc_admin/cdc_admin@helowin @ddl-oracle-cdc-admin.sql
-- ============================================================

-- 创建用户（如不存在）
-- DECLARE
--   v_count NUMBER;
-- BEGIN
--   SELECT COUNT(*) INTO v_count FROM dba_users WHERE username = 'CDC_ADMIN';
--   IF v_count = 0 THEN
--     EXECUTE IMMEDIATE 'CREATE USER cdc_admin IDENTIFIED BY cdc_admin';
--     EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE TO cdc_admin';
--     EXECUTE IMMEDIATE 'GRANT UNLIMITED TABLESPACE TO cdc_admin';
--   END IF;
-- END;
-- /

-- ============================================================
-- 1. APP_CONFIG — 应用运行时配置
-- ============================================================
CREATE TABLE cdc_admin.app_config (
    config_key   VARCHAR2(100)  NOT NULL,
    config_value VARCHAR2(1000) NOT NULL,
    description  VARCHAR2(500),
    created_at   DATE           DEFAULT SYSDATE,
    updated_at   DATE           DEFAULT SYSDATE,
    CONSTRAINT pk_app_config PRIMARY KEY (config_key)
);

COMMENT ON TABLE  cdc_admin.app_config              IS '应用运行时配置表';
COMMENT ON COLUMN cdc_admin.app_config.config_key   IS '配置键';
COMMENT ON COLUMN cdc_admin.app_config.config_value IS '配置值';
COMMENT ON COLUMN cdc_admin.app_config.description  IS '配置说明';

-- ============================================================
-- 2. CDC_DATASOURCES — 数据源配置
-- ============================================================
CREATE TABLE cdc_admin.cdc_datasources (
    id          VARCHAR2(100)  NOT NULL,
    name        VARCHAR2(200)  NOT NULL,
    host        VARCHAR2(200)  NOT NULL,
    port        NUMBER(5,0)    DEFAULT 1521,
    username    VARCHAR2(100)  NOT NULL,
    password    VARCHAR2(500)  NOT NULL,
    sid         VARCHAR2(100)  NOT NULL,
    description VARCHAR2(500),
    status      VARCHAR2(20)   DEFAULT 'UNTESTED',
    type        VARCHAR2(20)   DEFAULT 'ORACLE',
    created_at  DATE           DEFAULT SYSDATE,
    updated_at  DATE           DEFAULT SYSDATE,
    CONSTRAINT pk_cdc_datasources PRIMARY KEY (id));

COMMENT ON TABLE  cdc_admin.cdc_datasources             IS 'CDC 数据源配置表';
COMMENT ON COLUMN cdc_admin.cdc_datasources.id          IS '数据源唯一标识';
COMMENT ON COLUMN cdc_admin.cdc_datasources.name        IS '数据源名称';
COMMENT ON COLUMN cdc_admin.cdc_datasources.host        IS '数据库主机地址';
COMMENT ON COLUMN cdc_admin.cdc_datasources.port        IS '数据库端口';
COMMENT ON COLUMN cdc_admin.cdc_datasources.username    IS '数据库用户名';
COMMENT ON COLUMN cdc_admin.cdc_datasources.password    IS '数据库密码（AES 加密存储）';
COMMENT ON COLUMN cdc_admin.cdc_datasources.sid         IS 'Oracle SID 或数据库名';
COMMENT ON COLUMN cdc_admin.cdc_datasources.status      IS '连接状态: UNTESTED / SUCCESS / FAILED';

-- ============================================================
-- 3. CDC_TASKS — CDC 任务配置
-- ============================================================
CREATE TABLE cdc_admin.cdc_tasks (
    id            VARCHAR2(100)  NOT NULL,
    name          VARCHAR2(200)  NOT NULL,
    datasource_id VARCHAR2(100),
    schema_name   VARCHAR2(100)  NOT NULL,
    output_path   VARCHAR2(500),
    parallelism   NUMBER(3,0)    DEFAULT 2,
    split_size    NUMBER(10,0)   DEFAULT 8096,
    status        VARCHAR2(20)   DEFAULT 'CREATED',
    flink_job_id  VARCHAR2(100),
    savepoint_path VARCHAR2(500),
    tables        VARCHAR2(4000),
    created_at    DATE           DEFAULT SYSDATE,
    updated_at    DATE           DEFAULT SYSDATE,
    CONSTRAINT pk_cdc_tasks PRIMARY KEY (id)
);

COMMENT ON TABLE  cdc_admin.cdc_tasks                IS 'CDC 任务配置表';
COMMENT ON COLUMN cdc_admin.cdc_tasks.id             IS '任务唯一标识';
COMMENT ON COLUMN cdc_admin.cdc_tasks.name           IS '任务名称';
COMMENT ON COLUMN cdc_admin.cdc_tasks.datasource_id  IS '关联数据源 ID';
COMMENT ON COLUMN cdc_admin.cdc_tasks.schema_name    IS '监控的 Schema 名称';
COMMENT ON COLUMN cdc_admin.cdc_tasks.tables         IS '监控的表列表（JSON 数组）';
COMMENT ON COLUMN cdc_admin.cdc_tasks.output_path    IS 'CDC 输出路径';
COMMENT ON COLUMN cdc_admin.cdc_tasks.parallelism    IS 'Flink 并行度';
COMMENT ON COLUMN cdc_admin.cdc_tasks.split_size     IS 'LogMiner 分片大小';
COMMENT ON COLUMN cdc_admin.cdc_tasks.status         IS '任务状态: CREATED / RUNNING / STOPPED / FAILED';
COMMENT ON COLUMN cdc_admin.cdc_tasks.flink_job_id   IS '关联的 Flink Job ID';
COMMENT ON COLUMN cdc_admin.cdc_tasks.savepoint_path IS '最近一次 Savepoint 路径';

-- ============================================================
-- 4. RUNTIME_JOBS — Flink 运行时作业跟踪
-- ============================================================
CREATE TABLE cdc_admin.runtime_jobs (
    id                  VARCHAR2(100)  NOT NULL,
    task_id             VARCHAR2(100),
    flink_job_id        VARCHAR2(100),
    job_name            VARCHAR2(200),
    status              VARCHAR2(20)   DEFAULT 'PENDING',
    schema_name         VARCHAR2(100),
    parallelism         NUMBER(3,0),
    submit_time         DATE,
    start_time          DATE,
    end_time            DATE,
    error_message       VARCHAR2(4000),
    last_savepoint_path VARCHAR2(500),
    last_savepoint_time DATE,
    tables              VARCHAR2(4000),
    CONSTRAINT pk_runtime_jobs PRIMARY KEY (id)
);

COMMENT ON TABLE  cdc_admin.runtime_jobs                      IS 'Flink 运行时作业跟踪表';
COMMENT ON COLUMN cdc_admin.runtime_jobs.id                   IS '运行时作业唯一标识';
COMMENT ON COLUMN cdc_admin.runtime_jobs.task_id              IS '关联的任务配置 ID';
COMMENT ON COLUMN cdc_admin.runtime_jobs.flink_job_id         IS 'Flink 集群中的 Job ID';
COMMENT ON COLUMN cdc_admin.runtime_jobs.status               IS '作业状态: PENDING / SUBMITTING / RUNNING / FINISHED / FAILED / CANCELED';
COMMENT ON COLUMN cdc_admin.runtime_jobs.tables               IS '监控的表列表（JSON 数组）';
COMMENT ON COLUMN cdc_admin.runtime_jobs.error_message        IS '失败时的错误信息';
COMMENT ON COLUMN cdc_admin.runtime_jobs.last_savepoint_path  IS '最近一次 Savepoint 路径';
COMMENT ON COLUMN cdc_admin.runtime_jobs.last_savepoint_time  IS '最近一次 Savepoint 时间';

-- ============================================================
-- 5. CDC_FILES — CDC 输出文件映射
-- ============================================================
CREATE TABLE cdc_admin.cdc_files (
    id            VARCHAR2(100)  NOT NULL,
    file_path     VARCHAR2(500)  NOT NULL,
    file_name     VARCHAR2(200)  NOT NULL,
    table_name    VARCHAR2(100),
    file_size     NUMBER(20,0)   DEFAULT 0,
    line_count    NUMBER(20,0)   DEFAULT 0,
    last_modified DATE,
    created_at    DATE           DEFAULT SYSDATE,
    CONSTRAINT pk_cdc_files PRIMARY KEY (id)
);

COMMENT ON TABLE  cdc_admin.cdc_files               IS 'CDC 输出文件映射表（fileId → 真实路径）';
COMMENT ON COLUMN cdc_admin.cdc_files.id            IS '文件唯一标识（前端可见）';
COMMENT ON COLUMN cdc_admin.cdc_files.file_path     IS '文件真实路径（前端不可见）';
COMMENT ON COLUMN cdc_admin.cdc_files.file_name     IS '文件名';
COMMENT ON COLUMN cdc_admin.cdc_files.table_name    IS '对应的业务表名';
COMMENT ON COLUMN cdc_admin.cdc_files.file_size     IS '文件大小（字节）';
COMMENT ON COLUMN cdc_admin.cdc_files.line_count    IS '文件行数';
COMMENT ON COLUMN cdc_admin.cdc_files.last_modified IS '文件最后修改时间';

-- ============================================================
-- 默认数据
-- ============================================================
INSERT INTO cdc_admin.app_config (config_key, config_value, description) VALUES
    ('savepoint.target.directory', 'file:///opt/flink/savepoints', 'Flink Savepoint 存储目录');
INSERT INTO cdc_admin.app_config (config_key, config_value, description) VALUES
    ('checkpoint.directory', 'file:///opt/flink/checkpoints', 'Flink Checkpoint 存储目录');
INSERT INTO cdc_admin.app_config (config_key, config_value, description) VALUES
    ('flink.output.path', '/opt/flink/output/cdc', 'CDC 数据输出路径');
INSERT INTO cdc_admin.app_config (config_key, config_value, description) VALUES
    ('flink.job.jar.path', '/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar', 'Flink CDC 作业 JAR 路径');

COMMIT;
