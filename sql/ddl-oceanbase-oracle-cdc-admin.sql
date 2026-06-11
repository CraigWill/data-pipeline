-- ============================================================
-- CDC Admin 元数据表 DDL — OceanBase Oracle 模式
-- 租户: oratenant (Oracle 兼容模式)
-- Schema: CDC_ADMIN
-- 
-- 重要: 所有列不带 DEFAULT 子句，避免 flink-connector-oceanbase-cdc
--       的 Debezium 反序列化报错（无法解析 Oracle 模式的默认值字面量）
-- 所有 NUMBER 类型必须指定精度，避免 Debezium scale=-127 错误
-- ============================================================

-- ============================================================
-- 1. APP_CONFIG — 应用运行时配置
-- ============================================================
CREATE TABLE APP_CONFIG (
    ID            NUMBER(10,0) PRIMARY KEY,
    CONFIG_KEY    VARCHAR2(200),
    CONFIG_VALUE  VARCHAR2(2000),
    DESCRIPTION   VARCHAR2(500),
    CREATED_AT    TIMESTAMP(6),
    UPDATED_AT    TIMESTAMP(6)
);

-- ============================================================
-- 2. CDC_DATASOURCES — 数据源配置
-- ============================================================
CREATE TABLE CDC_DATASOURCES (
    ID            VARCHAR2(100) PRIMARY KEY,
    NAME          VARCHAR2(200),
    TYPE          VARCHAR2(50),
    HOST          VARCHAR2(200),
    PORT          NUMBER(10,0),
    USERNAME      VARCHAR2(200),
    PASSWORD      VARCHAR2(500),
    SID           VARCHAR2(200),
    DESCRIPTION   VARCHAR2(500),
    STATUS        VARCHAR2(50),
    CREATED_AT    TIMESTAMP(6),
    UPDATED_AT    TIMESTAMP(6)
);

-- ============================================================
-- 3. CDC_TASKS — CDC 任务配置
-- ============================================================
CREATE TABLE CDC_TASKS (
    ID             VARCHAR2(100) PRIMARY KEY,
    NAME           VARCHAR2(200),
    DATASOURCE_ID  VARCHAR2(100),
    SCHEMA_NAME    VARCHAR2(200),
    TABLE_LIST     VARCHAR2(2000),
    OUTPUT_PATH    VARCHAR2(500),
    PARALLELISM    NUMBER(10,0),
    SPLIT_SIZE     NUMBER(10,0),
    STATUS         VARCHAR2(50),
    FLINK_JOB_ID   VARCHAR2(100),
    ERROR_MESSAGE  VARCHAR2(2000),
    CREATED_AT     TIMESTAMP(6),
    UPDATED_AT     TIMESTAMP(6)
);

-- ============================================================
-- 4. CDC_FILES — CDC 输出文件映射
-- ============================================================
CREATE TABLE CDC_FILES (
    ID             VARCHAR2(100) PRIMARY KEY,
    FILE_PATH      VARCHAR2(1000),
    FILE_NAME      VARCHAR2(500),
    TABLE_NAME     VARCHAR2(200),
    FILE_SIZE      NUMBER(19,0),
    LINE_COUNT     NUMBER(19,0),
    LAST_MODIFIED  TIMESTAMP(6),
    CREATED_AT     TIMESTAMP(6)
);

-- ============================================================
-- 5. RUNTIME_JOBS — Flink 运行时作业跟踪
-- ============================================================
CREATE TABLE RUNTIME_JOBS (
    ID                   VARCHAR2(100) PRIMARY KEY,
    TASK_ID              VARCHAR2(100),
    FLINK_JOB_ID         VARCHAR2(100),
    JOB_NAME             VARCHAR2(200),
    STATUS               VARCHAR2(50),
    SCHEMA_NAME          VARCHAR2(200),
    PARALLELISM          NUMBER(10,0),
    SUBMIT_TIME          TIMESTAMP(6),
    START_TIME           TIMESTAMP(6),
    END_TIME             TIMESTAMP(6),
    ERROR_MESSAGE        VARCHAR2(2000),
    LAST_SAVEPOINT_PATH  VARCHAR2(500),
    LAST_SAVEPOINT_TIME  TIMESTAMP(6),
    TABLES               VARCHAR2(2000)
);

-- ============================================================
-- 6. CDC_TEST — CDC 测试表
-- ============================================================
CREATE TABLE CDC_TEST (
    ID     NUMBER(10,0) PRIMARY KEY,
    NAME   VARCHAR2(100),
    VALUE  VARCHAR2(500),
    TS     TIMESTAMP(6)
);

-- ============================================================
-- 默认数据
-- ============================================================
INSERT INTO APP_CONFIG (ID, CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, CREATED_AT, UPDATED_AT)
VALUES (1, 'savepoint.target.directory', 'file:///opt/flink/savepoints', 'Flink Savepoint 存储目录', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO APP_CONFIG (ID, CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, CREATED_AT, UPDATED_AT)
VALUES (2, 'checkpoint.directory', 'file:///opt/flink/checkpoints', 'Flink Checkpoint 存储目录', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO APP_CONFIG (ID, CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, CREATED_AT, UPDATED_AT)
VALUES (3, 'flink.output.path', '/opt/flink/output/cdc', 'CDC 数据输出路径', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO APP_CONFIG (ID, CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, CREATED_AT, UPDATED_AT)
VALUES (4, 'flink.job.jar.path', '/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar', 'Flink CDC 作业 JAR 路径', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CDC_TEST (ID, NAME, VALUE, TS) VALUES (1, 'hello', 'world', SYSTIMESTAMP);

COMMIT;
