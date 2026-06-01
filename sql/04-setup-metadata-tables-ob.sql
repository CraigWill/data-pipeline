-- ============================================
-- CDC 管理库元数据表 — OceanBase / MySQL 兼容
-- Database: cdc_admin
-- 包括：数据源表、任务配置表、运行时作业表、应用配置表、CDC文件表
-- ============================================
-- 执行方式:
--   mysql -h 172.17.0.1 -P 2881 -u cdc_admin -p cdc_admin < 04-setup-metadata-tables-ob.sql
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 1. cdc_datasources — 数据源配置
-- ============================================
CREATE TABLE IF NOT EXISTS cdc_datasources (
    id          VARCHAR(100)  NOT NULL PRIMARY KEY,
    name        VARCHAR(200)  NOT NULL,
    host        VARCHAR(200)  NOT NULL,
    port        INT           NOT NULL,
    username    VARCHAR(100)  NOT NULL,
    password    VARCHAR(200)  NOT NULL,
    sid         VARCHAR(100)  NOT NULL COMMENT 'Oracle SID 或 MySQL database 名',
    description VARCHAR(500),
    status      VARCHAR(20)   DEFAULT 'UNTESTED',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDC数据源配置表';

-- ============================================
-- 2. cdc_tasks — 任务配置
-- ============================================
CREATE TABLE IF NOT EXISTS cdc_tasks (
    id            VARCHAR(100)  NOT NULL PRIMARY KEY,
    name          VARCHAR(200)  NOT NULL,
    datasource_id VARCHAR(100)  NOT NULL,
    schema_name   VARCHAR(100)  NOT NULL,
    tables        LONGTEXT      NOT NULL COMMENT 'JSON数组格式的表名列表',
    output_path   VARCHAR(500),
    parallelism   INT           DEFAULT 4,
    split_size    INT           DEFAULT 8096,
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_datasource FOREIGN KEY (datasource_id)
        REFERENCES cdc_datasources(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDC任务配置表';

CREATE INDEX IF NOT EXISTS idx_tasks_datasource ON cdc_tasks(datasource_id);
CREATE INDEX IF NOT EXISTS idx_tasks_created    ON cdc_tasks(created_at);

-- ============================================
-- 3. runtime_jobs — Flink 运行时作业
-- ============================================
CREATE TABLE IF NOT EXISTS runtime_jobs (
    id                  VARCHAR(100)   NOT NULL PRIMARY KEY,
    task_id             VARCHAR(100)   NOT NULL,
    flink_job_id        VARCHAR(100),
    job_name            VARCHAR(200),
    status              VARCHAR(20)    DEFAULT 'SUBMITTING',
    schema_name         VARCHAR(100),
    tables              LONGTEXT       COMMENT 'JSON数组格式的表名列表',
    parallelism         INT,
    submit_time         DATETIME       DEFAULT CURRENT_TIMESTAMP,
    start_time          DATETIME,
    end_time            DATETIME,
    error_message       TEXT,
    last_savepoint_path VARCHAR(1024),
    last_savepoint_time DATETIME,
    CONSTRAINT fk_runtime_task FOREIGN KEY (task_id)
        REFERENCES cdc_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Flink运行时作业跟踪表';

CREATE INDEX IF NOT EXISTS idx_runtime_task        ON runtime_jobs(task_id);
CREATE INDEX IF NOT EXISTS idx_runtime_flink_job   ON runtime_jobs(flink_job_id);
CREATE INDEX IF NOT EXISTS idx_runtime_status      ON runtime_jobs(status);
CREATE INDEX IF NOT EXISTS idx_runtime_submit_time ON runtime_jobs(submit_time);

-- ============================================
-- 4. app_config — 应用运行时配置
-- ============================================
CREATE TABLE IF NOT EXISTS app_config (
    config_key   VARCHAR(100)  NOT NULL PRIMARY KEY,
    config_value VARCHAR(1000) NOT NULL,
    description  VARCHAR(500),
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用运行时配置表';

-- 插入默认配置（已存在则忽略）
INSERT IGNORE INTO app_config (config_key, config_value, description) VALUES
    ('savepoint.target.directory', 'file:///opt/flink/savepoints', 'Flink Savepoint 存储目录'),
    ('checkpoint.directory',       'file:///opt/flink/checkpoints', 'Flink Checkpoint 存储目录'),
    ('flink.output.path',          '/opt/flink/output/cdc',         'CDC 数据输出路径'),
    ('flink.job.jar.path',         '/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar', 'Flink CDC 作业 JAR 路径');

-- ============================================
-- 5. cdc_files — CDC 输出文件映射
-- ============================================
CREATE TABLE IF NOT EXISTS cdc_files (
    id            VARCHAR(32)   NOT NULL PRIMARY KEY,
    file_path     VARCHAR(1024) NOT NULL UNIQUE,
    file_name     VARCHAR(255)  NOT NULL,
    table_name    VARCHAR(200),
    file_size     BIGINT        DEFAULT 0,
    line_count    BIGINT        DEFAULT 0,
    last_modified DATETIME,
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDC输出文件映射表';

-- ============================================
-- 验证
-- ============================================
SELECT table_name, table_comment
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('cdc_datasources','cdc_tasks','runtime_jobs','app_config','cdc_files')
ORDER BY table_name;
