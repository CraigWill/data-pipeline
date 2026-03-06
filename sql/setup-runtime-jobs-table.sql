-- ============================================
-- 创建运行时作业表
-- 用于跟踪 Flink 作业的运行状态
-- ============================================

-- 删除已存在的表（如果存在）
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE flink_user.runtime_jobs CASCADE CONSTRAINTS';
    DBMS_OUTPUT.PUT_LINE('删除表 runtime_jobs');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/

-- 创建运行时作业表
CREATE TABLE flink_user.runtime_jobs (
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
    CONSTRAINT fk_runtime_task FOREIGN KEY (task_id) 
        REFERENCES flink_user.cdc_tasks(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_runtime_task ON flink_user.runtime_jobs(task_id);
CREATE INDEX idx_runtime_flink_job ON flink_user.runtime_jobs(flink_job_id);
CREATE INDEX idx_runtime_status ON flink_user.runtime_jobs(status);
CREATE INDEX idx_runtime_submit_time ON flink_user.runtime_jobs(submit_time);

-- 授权给 finance_user（如果需要）
BEGIN
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.runtime_jobs TO finance_user';
    DBMS_OUTPUT.PUT_LINE('已授权给 finance_user');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('授权失败（finance_user 可能不存在）: ' || SQLERRM);
END;
/

-- 添加注释
COMMENT ON TABLE flink_user.runtime_jobs IS 'Flink运行时作业跟踪表';
COMMENT ON COLUMN flink_user.runtime_jobs.id IS '运行时作业ID';
COMMENT ON COLUMN flink_user.runtime_jobs.task_id IS '关联的任务配置ID';
COMMENT ON COLUMN flink_user.runtime_jobs.flink_job_id IS 'Flink作业ID（异步获取）';
COMMENT ON COLUMN flink_user.runtime_jobs.status IS '作业状态：SUBMITTING, RUNNING, FINISHED, FAILED, CANCELED';
COMMENT ON COLUMN flink_user.runtime_jobs.tables IS '监控的表列表（JSON数组）';

COMMIT;

-- 验证表创建
SELECT 'runtime_jobs' AS table_name, COUNT(*) AS column_count 
FROM all_tab_columns 
WHERE owner = 'FLINK_USER' AND table_name = 'RUNTIME_JOBS';

PROMPT
PROMPT ============================================
PROMPT runtime_jobs 表创建完成！
PROMPT ============================================
