-- ============================================================
-- 创建 Flink 专用表空间和用户，分离业务表与 Flink 内部表
-- 
-- 执行方式: 以 sysdba 身份执行
--   sqlplus "/ as sysdba" @setup-flink-tablespace.sql
-- ============================================================

SET ECHO ON
SET FEEDBACK ON

-- 1. 创建 FLINK 表空间
-- ============================================================
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tablespaces WHERE tablespace_name = 'FLINK_TBS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLESPACE FLINK_TBS DATAFILE ''/home/oracle/app/oracle/oradata/helowin/flink_tbs01.dbf'' SIZE 100M AUTOEXTEND ON NEXT 50M MAXSIZE 2G';
        DBMS_OUTPUT.PUT_LINE('FLINK_TBS tablespace created.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('FLINK_TBS tablespace already exists.');
    END IF;
END;
/

-- 2. 创建 FLINK_USER 用户
-- ============================================================
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_users WHERE username = 'FLINK_USER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER FLINK_USER IDENTIFIED BY flink123 DEFAULT TABLESPACE FLINK_TBS TEMPORARY TABLESPACE TEMP QUOTA UNLIMITED ON FLINK_TBS';
        DBMS_OUTPUT.PUT_LINE('FLINK_USER created.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('FLINK_USER already exists.');
    END IF;
END;
/

-- 3. 授权 FLINK_USER
-- ============================================================
-- 基本权限
GRANT CONNECT, RESOURCE TO FLINK_USER;
GRANT CREATE SESSION TO FLINK_USER;
GRANT CREATE TABLE TO FLINK_USER;

-- CDC (LogMiner) 所需权限
GRANT SELECT ON V_$DATABASE TO FLINK_USER;
GRANT SELECT ON V_$LOG TO FLINK_USER;
GRANT SELECT ON V_$LOGFILE TO FLINK_USER;
GRANT SELECT ON V_$ARCHIVED_LOG TO FLINK_USER;
GRANT SELECT ON V_$ARCHIVE_DEST_STATUS TO FLINK_USER;
GRANT SELECT ON V_$TRANSACTION TO FLINK_USER;
GRANT SELECT ON V_$MYSTAT TO FLINK_USER;
GRANT SELECT ON V_$STATNAME TO FLINK_USER;
GRANT FLASHBACK ANY TABLE TO FLINK_USER;
GRANT SELECT ANY TABLE TO FLINK_USER;
GRANT SELECT ANY TRANSACTION TO FLINK_USER;
GRANT SELECT ANY DICTIONARY TO FLINK_USER;
GRANT EXECUTE ON DBMS_LOGMNR TO FLINK_USER;
GRANT EXECUTE ON DBMS_LOGMNR_D TO FLINK_USER;
GRANT LOGMINING TO FLINK_USER;

-- 允许 FLINK_USER 读取 FINANCE_USER 的表（CDC 需要）
GRANT SELECT ON FINANCE_USER.TRANS_INFO TO FLINK_USER;
GRANT SELECT ON FINANCE_USER.ACCOUNT_INFO TO FLINK_USER;

-- 4. 在 FLINK_USER schema 下创建 LOG_MINING_FLUSH 表
-- ============================================================
-- Debezium 需要这个表来 flush redo log buffer
-- 连接为 FLINK_USER 创建
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tables WHERE owner = 'FLINK_USER' AND table_name = 'LOG_MINING_FLUSH';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE FLINK_USER.LOG_MINING_FLUSH (LAST_SCN NUMBER DEFAULT 0 NOT NULL) TABLESPACE FLINK_TBS';
        EXECUTE IMMEDIATE 'INSERT INTO FLINK_USER.LOG_MINING_FLUSH VALUES (0)';
        COMMIT;
        DBMS_OUTPUT.PUT_LINE('FLINK_USER.LOG_MINING_FLUSH created.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('FLINK_USER.LOG_MINING_FLUSH already exists.');
    END IF;
END;
/

GRANT ALL ON FLINK_USER.LOG_MINING_FLUSH TO FLINK_USER;

-- 5. 清理 SYSTEM 用户下的 LOG_MINING_FLUSH
-- ============================================================
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tables WHERE owner = 'SYSTEM' AND table_name = 'LOG_MINING_FLUSH';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE SYSTEM.LOG_MINING_FLUSH PURGE';
        DBMS_OUTPUT.PUT_LINE('SYSTEM.LOG_MINING_FLUSH dropped.');
    END IF;
END;
/

-- 6. 清理 FINANCE_USER 下的 LOG_MINING_FLUSH（不属于业务表）
-- ============================================================
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tables WHERE owner = 'FINANCE_USER' AND table_name = 'LOG_MINING_FLUSH';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE FINANCE_USER.LOG_MINING_FLUSH PURGE';
        DBMS_OUTPUT.PUT_LINE('FINANCE_USER.LOG_MINING_FLUSH dropped.');
    END IF;
END;
/

-- 7. 验证
-- ============================================================
PROMPT
PROMPT === Verification ===
PROMPT

COL owner FORMAT A15
COL table_name FORMAT A25
COL tablespace_name FORMAT A15

PROMPT -- FLINK_USER tables:
SELECT owner, table_name, tablespace_name FROM dba_tables WHERE owner = 'FLINK_USER' ORDER BY table_name;

PROMPT -- FINANCE_USER tables (business only):
SELECT owner, table_name, tablespace_name FROM dba_tables WHERE owner = 'FINANCE_USER' ORDER BY table_name;

PROMPT -- Verify no LOG_MINING_FLUSH in SYSTEM:
SELECT owner, table_name FROM dba_tables WHERE table_name = 'LOG_MINING_FLUSH' ORDER BY owner;

PROMPT
PROMPT === Setup complete ===
PROMPT FLINK_USER credentials: flink123
PROMPT CDC should now use: username=FLINK_USER, schema=FINANCE_USER
PROMPT

EXIT;
