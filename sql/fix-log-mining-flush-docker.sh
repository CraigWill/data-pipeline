#!/bin/bash
# 在 Docker 容器中修复 log_mining_flush 表位置

set -e

ORACLE_CONTAINER=${ORACLE_CONTAINER:-oracle11g}
ORACLE_USER=${ORACLE_USER:-system}
ORACLE_PASSWORD=${ORACLE_PASSWORD:-password}
ORACLE_SID=${ORACLE_SID:-helowin}

echo "=========================================="
echo "修复 log_mining_flush 表位置"
echo "=========================================="
echo "容器: ${ORACLE_CONTAINER}"
echo "用户: ${ORACLE_USER}"
echo ""

# 检查容器是否运行
if ! docker ps --format '{{.Names}}' | grep -q "^${ORACLE_CONTAINER}$"; then
    echo "错误: 容器 ${ORACLE_CONTAINER} 未运行"
    exit 1
fi

echo "在容器中执行 SQL..."

docker exec -i ${ORACLE_CONTAINER} bash -c "
export ORACLE_HOME=/u01/app/oracle/product/11.2.0/xe
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=${ORACLE_SID}

sqlplus -S ${ORACLE_USER}/${ORACLE_PASSWORD} <<'EOSQL'
SET SERVEROUTPUT ON
SET ECHO OFF
SET FEEDBACK OFF
SET HEADING OFF

-- 删除 finance_user 中的旧表
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tables 
    WHERE owner = 'FINANCE_USER' AND table_name = 'LOG_MINING_FLUSH';
    
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE finance_user.log_mining_flush';
        DBMS_OUTPUT.PUT_LINE('✓ 已删除 finance_user.log_mining_flush');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ finance_user.log_mining_flush 不存在');
    END IF;
END;
/

-- 创建 SYSTEM 中的表
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_tables 
    WHERE owner = 'SYSTEM' AND table_name = 'LOG_MINING_FLUSH';
    
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE SYSTEM.log_mining_flush (scn NUMBER(19,0) NOT NULL, PRIMARY KEY (scn))';
        DBMS_OUTPUT.PUT_LINE('✓ 已创建 SYSTEM.log_mining_flush');
    ELSE
        DBMS_OUTPUT.PUT_LINE('✓ SYSTEM.log_mining_flush 已存在');
    END IF;
END;
/

-- 授予权限
BEGIN
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON SYSTEM.log_mining_flush TO finance_user';
    DBMS_OUTPUT.PUT_LINE('✓ 已授予 finance_user 权限');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1917 THEN -- ORA-01917: user or role does not exist
            RAISE;
        END IF;
END;
/

-- 验证表位置
SET HEADING ON
SET FEEDBACK ON
PROMPT
PROMPT 表位置:
SELECT owner, table_name, tablespace_name 
FROM dba_tables 
WHERE table_name = 'LOG_MINING_FLUSH';

PROMPT
PROMPT 权限:
SELECT grantee, privilege 
FROM dba_tab_privs 
WHERE table_name = 'LOG_MINING_FLUSH'
ORDER BY grantee, privilege;

EXIT;
EOSQL
"

echo ""
echo "=========================================="
echo "✓ 修复完成"
echo "=========================================="
echo ""
echo "下一步:"
echo "1. 重新构建项目: mvn clean package -DskipTests"
echo "2. 重新构建 Docker 镜像: docker-compose build"
echo "3. 重启 Flink 集群: docker-compose restart jobmanager jobmanager-standby taskmanager"
echo "4. 提交新的 CDC 任务"
echo ""
