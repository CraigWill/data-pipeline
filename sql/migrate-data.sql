-- ============================================
-- 迁移现有 JSON 配置到数据库
-- ============================================

SET SERVEROUTPUT ON;

-- 1. 插入数据源配置
INSERT INTO flink_user.cdc_datasources (
    id, name, host, port, username, password, sid, description, created_at, updated_at
) VALUES (
    'oracle-prod',
    '生产环境 Oracle',
    'host.docker.internal',
    1521,
    'finance_user',
    'password',
    'helowin',
    NULL,
    TO_TIMESTAMP('2026-03-03 13:20:00', 'YYYY-MM-DD HH24:MI:SS'),
    TO_TIMESTAMP('2026-03-03 13:20:00', 'YYYY-MM-DD HH24:MI:SS')
);

DBMS_OUTPUT.PUT_LINE('✓ 插入数据源: oracle-prod');

-- 2. 插入任务配置
INSERT INTO flink_user.cdc_tasks (
    id, name, datasource_id, schema_name, tables, output_path, parallelism, split_size, status, flink_job_id, created_at, updated_at
) VALUES (
    'task-1772523359496',
    'startall',
    'oracle-prod',
    'FINANCE_USER',
    '["ACCOUNT_INFO","TRANS_INFO"]',
    './output/cdc',
    4,
    8096,
    'CREATED',
    NULL,
    TO_TIMESTAMP('2026-03-03 07:35:59', 'YYYY-MM-DD HH24:MI:SS'),
    TO_TIMESTAMP('2026-03-03 07:35:59', 'YYYY-MM-DD HH24:MI:SS')
);

DBMS_OUTPUT.PUT_LINE('✓ 插入任务: task-1772523359496 (startall)');

INSERT INTO flink_user.cdc_tasks (
    id, name, datasource_id, schema_name, tables, output_path, parallelism, split_size, status, flink_job_id, created_at, updated_at
) VALUES (
    'task-1772763912898',
    '333',
    'oracle-prod',
    'FINANCE_USER',
    '["ACCOUNT_INFO"]',
    './output/cdc',
    4,
    8096,
    'CREATED',
    NULL,
    TO_TIMESTAMP('2026-03-06 02:25:12', 'YYYY-MM-DD HH24:MI:SS'),
    TO_TIMESTAMP('2026-03-06 02:25:12', 'YYYY-MM-DD HH24:MI:SS')
);

DBMS_OUTPUT.PUT_LINE('✓ 插入任务: task-1772763912898 (333)');

INSERT INTO flink_user.cdc_tasks (
    id, name, datasource_id, schema_name, tables, output_path, parallelism, split_size, status, flink_job_id, created_at, updated_at
) VALUES (
    'task-1772771705229',
    'table2',
    'oracle-prod',
    'FINANCE_USER',
    '["ACCOUNT_INFO","TRANS_INFO"]',
    './output/cdc',
    4,
    8096,
    'CREATED',
    NULL,
    TO_TIMESTAMP('2026-03-06 04:35:05', 'YYYY-MM-DD HH24:MI:SS'),
    TO_TIMESTAMP('2026-03-06 04:35:05', 'YYYY-MM-DD HH24:MI:SS')
);

DBMS_OUTPUT.PUT_LINE('✓ 插入任务: task-1772771705229 (table2)');

INSERT INTO flink_user.cdc_tasks (
    id, name, datasource_id, schema_name, tables, output_path, parallelism, split_size, status, flink_job_id, created_at, updated_at
) VALUES (
    'task-verify-1772672324',
    'verify',
    'oracle-prod',
    'FINANCE_USER',
    '["TRANS_INFO"]',
    './output/cdc',
    2,
    8096,
    'CREATED',
    NULL,
    TO_TIMESTAMP('2026-03-05 00:58:44', 'YYYY-MM-DD HH24:MI:SS'),
    TO_TIMESTAMP('2026-03-05 00:58:44', 'YYYY-MM-DD HH24:MI:SS')
);

DBMS_OUTPUT.PUT_LINE('✓ 插入任务: task-verify-1772672324 (verify)');

COMMIT;

-- 3. 验证数据
PROMPT
PROMPT ============================================
PROMPT 数据迁移完成！
PROMPT ============================================
PROMPT

SELECT '数据源' AS 类型, COUNT(*) AS 数量 FROM flink_user.cdc_datasources
UNION ALL
SELECT '任务' AS 类型, COUNT(*) AS 数量 FROM flink_user.cdc_tasks;

PROMPT
PROMPT 数据源列表:
SELECT id, name, host FROM flink_user.cdc_datasources;

PROMPT
PROMPT 任务列表:
SELECT id, name, schema_name, status FROM flink_user.cdc_tasks;
