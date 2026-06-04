-- ============================================================
-- CDC Admin 元数据表 DDL — OceanBase Oracle 兼容模式
-- 租户: oratenant（Oracle 兼容）
-- 用户: CDC_ADMIN
-- ============================================================
-- 执行方式:
--   obclient -h172.22.0.1 -P2881 -ucdc_admin@oratenant -p -DCDC_ADMIN
--   然后执行本脚本
-- ============================================================

-- ============================================================
-- 1. APP_CONFIG — 应用运行时配置
-- ============================================================
CREATE TABLE app_config (
    id           VARCHAR2(100)  NOT NULL,
    config_key   VARCHAR2(100)  NOT NULL,
    config_value VARCHAR2(1000) NOT NULL,
    description  VARCHAR2(500),
    created_at   TIMESTAMP DEFAULT SYSTIMESTAMP,
    updated_at   TIMESTAMP DEFAULT SYSTIMESTAMP,
    CONSTRAINT pk_app_config PRIMARY KEY (id)
);

-- ============================================================
-- 2. CDC_DATASOURCES — 数据源配置
-- ============================================================
CREATE TABLE cdc_datasources (
    id          VARCHAR2(100)  NOT NULL,
    name        VARCHAR2(200)  NOT NULL,
    host        VARCHAR2(200)  NOT NULL,
    port        NUMBER(5)      DEFAULT 1521,
    username    VARCHAR2(100)  NOT NULL,
    password    VARCHAR2(500)  NOT NULL,
    sid         VARCHAR2(100)  NOT NULL,
    description VARCHAR2(500),
    status      VARCHAR2(20)   DEFAULT 'UNTESTED',
    type        VARCHAR2(20)   DEFAULT 'ORACLE',
    created_at  TIMESTAMP DEFAULT SYSTIMESTAMP,
    updated_at  TIMESTAMP DEFAULT SYSTIMESTAMP,
    CONSTRAINT pk_cdc_datasources PRIMARY KEY (id)
);

-- ============================================================
-- 3. CDC_TASKS — CDC 任务配置
-- ============================================================
CREATE TABLE cdc_tasks (
    id             VARCHAR2(100)  NOT NULL,
    name           VARCHAR2(200)  NOT NULL,
    datasource_id  VARCHAR2(100),
    schema_name    VARCHAR2(100)  NOT NULL,
    tables         VARCHAR2(4000),
    output_path    VARCHAR2(500),
    parallelism    NUMBER(3)      DEFAULT 2,
    split_size     NUMBER(10)     DEFAULT 8096,
    status         VARCHAR2(20)   DEFAULT 'CREATED',
    flink_job_id   VARCHAR2(100),
    savepoint_path VARCHAR2(500),
    created_at     TIMESTAMP DEFAULT SYSTIMESTAMP,
    updated_at     TIMESTAMP DEFAULT SYSTIMESTAMP,
    CONSTRAINT pk_cdc_tasks PRIMARY KEY (id)
);

-- ============================================================
-- 4. RUNTIME_JOBS — Flink 运行时作业跟踪
-- ============================================================
CREATE TABLE runtime_jobs (
    id                  VARCHAR2(100)  NOT NULL,
    task_id             VARCHAR2(100),
    flink_job_id        VARCHAR2(100),
    job_name            VARCHAR2(200),
    status              VARCHAR2(20)   DEFAULT 'PENDING',
    schema_name         VARCHAR2(100),
    tables              VARCHAR2(4000),
    parallelism         NUMBER(3),
    submit_time         TIMESTAMP,
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    error_message       VARCHAR2(4000),
    last_savepoint_path VARCHAR2(500),
    last_savepoint_time TIMESTAMP,
    CONSTRAINT pk_runtime_jobs PRIMARY KEY (id)
);

-- ============================================================
-- 5. CDC_FILES — CDC 输出文件映射
-- ============================================================
CREATE TABLE cdc_files (
    id            VARCHAR2(100)  NOT NULL,
    file_path     VARCHAR2(500)  NOT NULL,
    file_name     VARCHAR2(200)  NOT NULL,
    table_name    VARCHAR2(100),
    file_size     NUMBER(20)     DEFAULT 0,
    line_count    NUMBER(20)     DEFAULT 0,
    last_modified TIMESTAMP,
    created_at    TIMESTAMP DEFAULT SYSTIMESTAMP,
    CONSTRAINT pk_cdc_files PRIMARY KEY (id),
    CONSTRAINT uk_cdc_files_path UNIQUE (file_path)
);

-- ============================================================
-- 默认数据
-- ============================================================
INSERT INTO app_config (id, config_key, config_value, description) VALUES
    ('cfg_savepoint', 'savepoint.target.directory', 'file:///opt/flink/savepoints', 'Flink Savepoint 存储目录');
INSERT INTO app_config (id, config_key, config_value, description) VALUES
    ('cfg_checkpoint', 'checkpoint.directory', 'file:///opt/flink/checkpoints', 'Flink Checkpoint 存储目录');
INSERT INTO app_config (id, config_key, config_value, description) VALUES
    ('cfg_output', 'flink.output.path', '/opt/flink/output/cdc', 'CDC 数据输出路径');
INSERT INTO app_config (id, config_key, config_value, description) VALUES
    ('cfg_jar', 'flink.job.jar.path', '/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar', 'Flink CDC 作业 JAR 路径');

COMMIT;

-- ============================================================
-- 验证
-- ============================================================
SELECT table_name FROM user_tables ORDER BY table_name;
