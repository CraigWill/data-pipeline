-- ============================================
-- 数据迁移脚本：FINANCE_USER → CDC_ADMIN
-- 将管理表数据从 FINANCE_USER schema 迁移到 CDC_ADMIN schema
-- ============================================
-- 前置条件: 已执行 05-setup-cdc-admin-schema.sql 创建 CDC_ADMIN 用户和表
-- 执行方式: sqlplus / as sysdba @06-migrate-to-cdc-admin.sql

SET SERVEROUTPUT ON;

PROMPT ==========================================
PROMPT  数据迁移: FINANCE_USER → CDC_ADMIN
PROMPT ==========================================

-- ============================================
-- 1. 迁移 CDC_DATASOURCES（数据源配置）
-- ============================================
PROMPT
PROMPT [1/5] 迁移 CDC_DATASOURCES...

DECLARE
    v_src_count NUMBER := 0;
    v_dst_count NUMBER := 0;
    v_migrated NUMBER := 0;
BEGIN
    -- 检查源表是否存在
    SELECT COUNT(*) INTO v_src_count FROM all_tables 
    WHERE owner = 'FINANCE_USER' AND table_name = 'CDC_DATASOURCES';
    
    IF v_src_count = 0 THEN
        DBMS_OUTPUT.PUT_LINE('  ⚠ FINANCE_USER.CDC_DATASOURCES 不存在，跳过');
    ELSE
        -- 统计源数据
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM finance_user.cdc_datasources' INTO v_src_count;
        DBMS_OUTPUT.PUT_LINE('  源表记录数: ' || v_src_count);
        
        IF v_src_count > 0 THEN
            -- 迁移数据（MERGE 避免重复）
            EXECUTE IMMEDIATE '
                MERGE INTO cdc_admin.cdc_datasources t
                USING finance_user.cdc_datasources s
                ON (t.id = s.id)
                WHEN NOT MATCHED THEN
                    INSERT (id, name, host, port, username, password, sid, description, status, created_at, updated_at)
                    VALUES (s.id, s.name, s.host, s.port, s.username, s.password, s.sid, s.description, 
                            NVL(s.status, ''UNTESTED''), s.created_at, s.updated_at)
            ';
            v_migrated := SQL%ROWCOUNT;
            COMMIT;
            DBMS_OUTPUT.PUT_LINE('  ✅ 迁移完成: ' || v_migrated || ' 条记录');
        ELSE
            DBMS_OUTPUT.PUT_LINE('  ℹ 源表为空，无需迁移');
        END IF;
    END IF;
END;
/

-- ============================================
-- 2. 迁移 CDC_TASKS（任务配置）
-- ============================================
PROMPT
PROMPT [2/5] 迁移 CDC_TASKS...

DECLARE
    v_src_count NUMBER := 0;
    v_migrated NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_src_count FROM all_tables 
    WHERE owner = 'FINANCE_USER' AND table_name = 'CDC_TASKS';
    
    IF v_src_count = 0 THEN
        DBMS_OUTPUT.PUT_LINE('  ⚠ FINANCE_USER.CDC_TASKS 不存在，跳过');
    ELSE
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM finance_user.cdc_tasks' INTO v_src_count;
        DBMS_OUTPUT.PUT_LINE('  源表记录数: ' || v_src_count);
        
        IF v_src_count > 0 THEN
            EXECUTE IMMEDIATE '
                MERGE INTO cdc_admin.cdc_tasks t
                USING finance_user.cdc_tasks s
                ON (t.id = s.id)
                WHEN NOT MATCHED THEN
                    INSERT (id, name, datasource_id, schema_name, tables, output_path, 
                            parallelism, split_size, status, flink_job_id, created_at, updated_at)
                    VALUES (s.id, s.name, s.datasource_id, s.schema_name, s.tables, s.output_path,
                            s.parallelism, s.split_size, s.status, s.flink_job_id, s.created_at, s.updated_at)
            ';
            v_migrated := SQL%ROWCOUNT;
            COMMIT;
            DBMS_OUTPUT.PUT_LINE('  ✅ 迁移完成: ' || v_migrated || ' 条记录');
        ELSE
            DBMS_OUTPUT.PUT_LINE('  ℹ 源表为空，无需迁移');
        END IF;
    END IF;
END;
/

-- ============================================
-- 3. 迁移 RUNTIME_JOBS（运行时作业）
-- ============================================
PROMPT
PROMPT [3/5] 迁移 RUNTIME_JOBS...

DECLARE
    v_src_count NUMBER := 0;
    v_migrated NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_src_count FROM all_tables 
    WHERE owner = 'FINANCE_USER' AND table_name = 'RUNTIME_JOBS';
    
    IF v_src_count = 0 THEN
        DBMS_OUTPUT.PUT_LINE('  ⚠ FINANCE_USER.RUNTIME_JOBS 不存在，跳过');
    ELSE
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM finance_user.runtime_jobs' INTO v_src_count;
        DBMS_OUTPUT.PUT_LINE('  源表记录数: ' || v_src_count);
        
        IF v_src_count > 0 THEN
            EXECUTE IMMEDIATE '
                MERGE INTO cdc_admin.runtime_jobs t
                USING finance_user.runtime_jobs s
                ON (t.id = s.id)
                WHEN NOT MATCHED THEN
                    INSERT (id, task_id, task_name, schema_name, tables, parallelism,
                            flink_job_id, status, error_message, savepoint_path,
                            started_at, finished_at, created_at, updated_at)
                    VALUES (s.id, s.task_id, s.task_name, s.schema_name, s.tables, s.parallelism,
                            s.flink_job_id, s.status, s.error_message, s.savepoint_path,
                            s.started_at, s.finished_at, s.created_at, s.updated_at)
            ';
            v_migrated := SQL%ROWCOUNT;
            COMMIT;
            DBMS_OUTPUT.PUT_LINE('  ✅ 迁移完成: ' || v_migrated || ' 条记录');
        ELSE
            DBMS_OUTPUT.PUT_LINE('  ℹ 源表为空，无需迁移');
        END IF;
    END IF;
END;
/

-- ============================================
-- 4. 迁移 CDC_FILES（文件注册表）
-- ============================================
PROMPT
PROMPT [4/5] 迁移 CDC_FILES...

DECLARE
    v_src_count NUMBER := 0;
    v_migrated NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_src_count FROM all_tables 
    WHERE owner = 'FINANCE_USER' AND table_name = 'CDC_FILES';
    
    IF v_src_count = 0 THEN
        DBMS_OUTPUT.PUT_LINE('  ⚠ FINANCE_USER.CDC_FILES 不存在，跳过');
    ELSE
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM finance_user.cdc_files' INTO v_src_count;
        DBMS_OUTPUT.PUT_LINE('  源表记录数: ' || v_src_count);
        
        IF v_src_count > 0 THEN
            EXECUTE IMMEDIATE '
                MERGE INTO cdc_admin.cdc_files t
                USING finance_user.cdc_files s
                ON (t.id = s.id)
                WHEN NOT MATCHED THEN
                    INSERT (id, relative_path, file_name, table_name, file_size, line_count, last_modified, created_at)
                    VALUES (s.id, s.relative_path, s.file_name, s.table_name, s.file_size, s.line_count, s.last_modified, s.created_at)
            ';
            v_migrated := SQL%ROWCOUNT;
            COMMIT;
            DBMS_OUTPUT.PUT_LINE('  ✅ 迁移完成: ' || v_migrated || ' 条记录');
        ELSE
            DBMS_OUTPUT.PUT_LINE('  ℹ 源表为空，无需迁移');
        END IF;
    END IF;
END;
/

-- ============================================
-- 5. 迁移 APP_CONFIG（应用配置）
-- ============================================
PROMPT
PROMPT [5/5] 迁移 APP_CONFIG...

DECLARE
    v_src_count NUMBER := 0;
    v_migrated NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_src_count FROM all_tables 
    WHERE owner = 'FINANCE_USER' AND table_name = 'APP_CONFIG';
    
    IF v_src_count = 0 THEN
        DBMS_OUTPUT.PUT_LINE('  ⚠ FINANCE_USER.APP_CONFIG 不存在，跳过');
    ELSE
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM finance_user.app_config' INTO v_src_count;
        DBMS_OUTPUT.PUT_LINE('  源表记录数: ' || v_src_count);
        
        IF v_src_count > 0 THEN
            EXECUTE IMMEDIATE '
                MERGE INTO cdc_admin.app_config t
                USING finance_user.app_config s
                ON (t.config_key = s.config_key)
                WHEN NOT MATCHED THEN
                    INSERT (config_key, config_value, description, created_at, updated_at)
                    VALUES (s.config_key, s.config_value, s.description, s.created_at, s.updated_at)
                WHEN MATCHED THEN
                    UPDATE SET config_value = s.config_value, updated_at = CURRENT_TIMESTAMP
            ';
            v_migrated := SQL%ROWCOUNT;
            COMMIT;
            DBMS_OUTPUT.PUT_LINE('  ✅ 迁移完成: ' || v_migrated || ' 条记录');
        ELSE
            DBMS_OUTPUT.PUT_LINE('  ℹ 源表为空，无需迁移');
        END IF;
    END IF;
END;
/

-- ============================================
-- 6. 验证迁移结果
-- ============================================
PROMPT
PROMPT === 迁移结果验证 ===

PROMPT
PROMPT CDC_ADMIN 表数据统计:
SELECT 'CDC_DATASOURCES' AS table_name, COUNT(*) AS row_count FROM cdc_admin.cdc_datasources
UNION ALL
SELECT 'CDC_TASKS', COUNT(*) FROM cdc_admin.cdc_tasks
UNION ALL
SELECT 'RUNTIME_JOBS', COUNT(*) FROM cdc_admin.runtime_jobs
UNION ALL
SELECT 'CDC_FILES', COUNT(*) FROM cdc_admin.cdc_files
UNION ALL
SELECT 'APP_CONFIG', COUNT(*) FROM cdc_admin.app_config;

-- ============================================
-- 7. 清理旧表（可选 — 确认迁移成功后手动执行）
-- ============================================
PROMPT
PROMPT ==========================================
PROMPT  迁移完成！
PROMPT
PROMPT  验证无误后，可手动清理 FINANCE_USER 中的旧表:
PROMPT    DROP TABLE finance_user.cdc_datasources CASCADE CONSTRAINTS;
PROMPT    DROP TABLE finance_user.cdc_tasks CASCADE CONSTRAINTS;
PROMPT    DROP TABLE finance_user.runtime_jobs CASCADE CONSTRAINTS;
PROMPT    DROP TABLE finance_user.cdc_files CASCADE CONSTRAINTS;
PROMPT    DROP TABLE finance_user.app_config CASCADE CONSTRAINTS;
PROMPT
PROMPT  注意: LOG_MINING_FLUSH 表必须保留在 FINANCE_USER 中！
PROMPT ==========================================
