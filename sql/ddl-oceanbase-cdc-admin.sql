-- ============================================================
-- CDC Admin 元数据表 DDL — OceanBase / MySQL 兼容
-- Database: cdcdb (或自定义数据库名)
-- 提取自: oracle-cdcdb 容器 (helowin / CDC_ADMIN)，转换为 MySQL 兼容语法
-- 包含: app_config, cdc_datasources, cdc_files, cdc_tasks, runtime_jobs
-- ============================================================
-- 执行方式:
--   mysql -h 172.17.0.1 -P 2881 -u root@test -p cdcdb < ddl-oceanbase-cdc-admin.sql
-- 或在 OceanBase 客户端中:
--   source /path/to/ddl-oceanbase-cdc-admin.sql
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 1. app_config — 应用运行时配置
-- ============================================================
CREATE TABLE IF NOT EXISTS app_config (
    id           VARCHAR(100)   NOT NULL,
    config_key   VARCHAR(100)   NOT NULL,
    config_value VARCHAR(1000)  NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用运行时配置表';

-- ============================================================
-- 2. cdc_datasources — 数据源配置
-- ============================================================
CREATE TABLE IF NOT EXISTS cdc_datasources (
    id          VARCHAR(100)   NOT NULL,
    name        VARCHAR(200)   NOT NULL,
    host        VARCHAR(200)   NOT NULL,
    port        INT            NOT NULL DEFAULT 1521,
    username    VARCHAR(100)   NOT NULL,
    password    VARCHAR(500)   NOT NULL  COMMENT 'AES 加密存储（{AES}: 前缀标识密文）',
    sid         VARCHAR(100)   NOT NULL  COMMENT 'Oracle SID 或数据库名',
    description VARCHAR(500),
    status      VARCHAR(20)    DEFAULT 'UNTESTED' COMMENT 'UNTESTED / SUCCESS / FAILED',
    type        VARCHAR(20)    DEFAULT 'ORACLE'   COMMENT 'ORACLE / MYSQL / OCEANBASE / POSTGRES',
    created_at  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDC 数据源配置表';

-- ============================================================
-- 3. cdc_tasks — CDC 任务配置
-- ============================================================
CREATE TABLE IF NOT EXISTS cdc_tasks (
    id             VARCHAR(100)   NOT NULL,
    name           VARCHAR(200)   NOT NULL,
    datasource_id  VARCHAR(100),
    schema_name    VARCHAR(100)   NOT NULL,
    output_path    VARCHAR(500),
    parallelism    INT            DEFAULT 2,
    split_size     INT            DEFAULT 8096,
    status         VARCHAR(20)    DEFAULT 'CREATED' COMMENT 'CREATED / RUNNING / STOPPED / FAILED',
    flink_job_id   VARCHAR(100),
    savepoint_path VARCHAR(500),
    tables         TEXT                              COMMENT '监控的表列表（JSON 数组）',
    created_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDC 任务配置表';

-- ============================================================
-- 4. runtime_jobs — Flink 运行时作业跟踪
-- ============================================================
CREATE TABLE IF NOT EXISTS runtime_jobs (
    id                  VARCHAR(100)   NOT NULL,
    task_id             VARCHAR(100),
    flink_job_id        VARCHAR(100),
    job_name            VARCHAR(200),
    status              VARCHAR(20)    DEFAULT 'PENDING' COMMENT 'PENDING / SUBMITTING / RUNNING / FINISHED / FAILED / CANCELED',
    schema_name         VARCHAR(100),
    parallelism         INT,
    submit_time         TIMESTAMP      NULL DEFAULT NULL,
    start_time          TIMESTAMP      NULL DEFAULT NULL,
    end_time            TIMESTAMP      NULL DEFAULT NULL,
    error_message       TEXT,
    last_savepoint_path VARCHAR(500),
    last_savepoint_time TIMESTAMP      NULL DEFAULT NULL,
    tables              TEXT                COMMENT '监控的表列表（JSON 数组）',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Flink 运行时作业跟踪表';

-- ============================================================
-- 5. cdc_files — CDC 输出文件映射
-- ============================================================
CREATE TABLE IF NOT EXISTS cdc_files (
    id            VARCHAR(100)   NOT NULL,
    file_path     VARCHAR(500)   NOT NULL UNIQUE COMMENT '文件真实路径（前端不可见）',
    file_name     VARCHAR(200)   NOT NULL,
    table_name    VARCHAR(100),
    file_size     BIGINT         DEFAULT 0,
    line_count    BIGINT         DEFAULT 0,
    last_modified TIMESTAMP      NULL DEFAULT NULL,
    created_at    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDC 输出文件映射表（fileId → 真实路径）';

-- ============================================================
-- 默认数据
-- ============================================================
INSERT IGNORE INTO app_config (config_key, config_value, description) VALUES
    ('savepoint.target.directory', 'file:///opt/flink/savepoints', 'Flink Savepoint 存储目录'),
    ('checkpoint.directory',       'file:///opt/flink/checkpoints', 'Flink Checkpoint 存储目录'),
    ('flink.output.path',          '/opt/flink/output/cdc',         'CDC 数据输出路径'),
    ('flink.job.jar.path',         '/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar', 'Flink CDC 作业 JAR 路径');
