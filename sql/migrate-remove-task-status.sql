-- 从 cdc_tasks 表移除 status 和 flink_job_id 列
-- 这两个字段属于运行时状态，应由 runtime_jobs 表管理

-- 删除 status 索引
BEGIN
    EXECUTE IMMEDIATE 'DROP INDEX flink_user.idx_tasks_status';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- 删除 status 和 flink_job_id 列
ALTER TABLE flink_user.cdc_tasks DROP COLUMN status;
ALTER TABLE flink_user.cdc_tasks DROP COLUMN flink_job_id;

COMMIT;

PROMPT 已从 cdc_tasks 表移除 status 和 flink_job_id 列
