-- 给 runtime_jobs 表添加 savepoint 相关列
ALTER TABLE runtime_jobs ADD COLUMN last_savepoint_path VARCHAR(1024);
ALTER TABLE runtime_jobs ADD COLUMN last_savepoint_time TIMESTAMP;
