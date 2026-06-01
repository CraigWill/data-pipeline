-- ============================================
-- 创建应用配置表 app_config
-- 用于存储系统运行时配置（如 savepoint 目录等）
-- ============================================

-- 创建表（如果不存在）
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'APP_CONFIG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE app_config (
                config_key   VARCHAR2(100) PRIMARY KEY,
                config_value VARCHAR2(1000) NOT NULL,
                description  VARCHAR2(500),
                created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ';
        DBMS_OUTPUT.PUT_LINE('✅ APP_CONFIG 表创建成功');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ℹ️ APP_CONFIG 表已存在，跳过创建');
    END IF;
END;
/

-- 插入默认配置
MERGE INTO app_config t
USING (SELECT 'savepoint.target.directory' AS config_key FROM dual) s
ON (t.config_key = s.config_key)
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, description)
    VALUES ('savepoint.target.directory', 'file:///opt/flink/savepoints', 'Flink Savepoint 存储目录');

MERGE INTO app_config t
USING (SELECT 'checkpoint.directory' AS config_key FROM dual) s
ON (t.config_key = s.config_key)
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, description)
    VALUES ('checkpoint.directory', 'file:///opt/flink/checkpoints', 'Flink Checkpoint 存储目录');

MERGE INTO app_config t
USING (SELECT 'flink.output.path' AS config_key FROM dual) s
ON (t.config_key = s.config_key)
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, description)
    VALUES ('flink.output.path', '/opt/flink/output/cdc', 'CDC 数据输出路径');

MERGE INTO app_config t
USING (SELECT 'flink.job.jar.path' AS config_key FROM dual) s
ON (t.config_key = s.config_key)
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, description)
    VALUES ('flink.job.jar.path', '/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar', 'Flink CDC 作业 JAR 路径');

COMMIT;

-- 验证
SELECT config_key, config_value, description FROM app_config ORDER BY config_key;
