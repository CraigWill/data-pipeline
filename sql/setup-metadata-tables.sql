-- ============================================
-- 创建元数据表用于存储任务和数据源配置
-- Schema: flink_user
-- ============================================

-- 1. 数据源配置表
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

COMMENT ON TABLE flink_user.cdc_datasources IS 'CDC数据源配置表';
COMMENT ON COLUMN flink_user.cdc_datasources.id IS '数据源唯一标识';
COMMENT ON COLUMN flink_user.cdc_datasources.name IS '数据源名称';
COMMENT ON COLUMN flink_user.cdc_datasources.host IS '数据库主机地址';
COMMENT ON COLUMN flink_user.cdc_datasources.port IS '数据库端口';
COMMENT ON COLUMN flink_user.cdc_datasources.username IS '数据库用户名';
COMMENT ON COLUMN flink_user.cdc_datasources.password IS '数据库密码';
COMMENT ON COLUMN flink_user.cdc_datasources.sid IS '数据库SID';
COMMENT ON COLUMN flink_user.cdc_datasources.description IS '数据源描述';

-- 2. CDC任务配置表
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

COMMENT ON TABLE flink_user.cdc_tasks IS 'CDC任务配置表';
COMMENT ON COLUMN flink_user.cdc_tasks.id IS '任务唯一标识';
COMMENT ON COLUMN flink_user.cdc_tasks.name IS '任务名称';
COMMENT ON COLUMN flink_user.cdc_tasks.datasource_id IS '关联的数据源ID';
COMMENT ON COLUMN flink_user.cdc_tasks.schema_name IS 'Oracle Schema名称';
COMMENT ON COLUMN flink_user.cdc_tasks.tables IS '表名列表(JSON数组格式)';
COMMENT ON COLUMN flink_user.cdc_tasks.output_path IS '输出路径';
COMMENT ON COLUMN flink_user.cdc_tasks.parallelism IS '并行度';
COMMENT ON COLUMN flink_user.cdc_tasks.split_size IS '分片大小';
COMMENT ON COLUMN flink_user.cdc_tasks.status IS '任务状态: CREATED, RUNNING, STOPPED, FAILED';
COMMENT ON COLUMN flink_user.cdc_tasks.flink_job_id IS 'Flink作业ID';

-- 3. 创建索引
CREATE INDEX idx_tasks_datasource ON flink_user.cdc_tasks(datasource_id);
CREATE INDEX idx_tasks_status ON flink_user.cdc_tasks(status);
CREATE INDEX idx_tasks_created ON flink_user.cdc_tasks(created_at);

-- 4. 创建更新时间触发器
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

-- 5. 授权给 finance_user (如果需要)
GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.cdc_datasources TO finance_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.cdc_tasks TO finance_user;

COMMIT;
