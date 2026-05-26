-- Check if tables exist in finance_user schema
SELECT table_name FROM user_tables WHERE table_name IN ('RUNTIME_JOBS', 'CDC_DATASOURCES', 'CDC_TASKS', 'APP_CONFIG', 'CDC_FILES') ORDER BY table_name;

-- Check table counts
SELECT 'RUNTIME_JOBS' as table_name, COUNT(*) as row_count FROM runtime_jobs UNION ALL
SELECT 'CDC_DATASOURCES', COUNT(*) FROM cdc_datasources UNION ALL
SELECT 'CDC_TASKS', COUNT(*) FROM cdc_tasks UNION ALL
SELECT 'APP_CONFIG', COUNT(*) FROM app_config UNION ALL
SELECT 'CDC_FILES', COUNT(*) FROM cdc_files;