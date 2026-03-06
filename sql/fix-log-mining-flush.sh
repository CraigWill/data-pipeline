#!/bin/bash
# 修复 log_mining_flush 表位置的脚本
# 将表从 finance_user schema 移动到 SYSTEM schema

set -e

# 配置
ORACLE_HOST=${ORACLE_HOST:-localhost}
ORACLE_PORT=${ORACLE_PORT:-1521}
ORACLE_SID=${ORACLE_SID:-helowin}
ORACLE_USER=${ORACLE_USER:-system}
ORACLE_PASSWORD=${ORACLE_PASSWORD:-password}

echo "=========================================="
echo "修复 log_mining_flush 表位置"
echo "=========================================="
echo "Oracle: ${ORACLE_USER}@${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_SID}"
echo ""

# 检查 sqlplus 是否可用
if ! command -v sqlplus &> /dev/null; then
    echo "错误: sqlplus 未安装或不在 PATH 中"
    echo "请安装 Oracle Instant Client 或在 Oracle 容器中运行此脚本"
    exit 1
fi

# 执行 SQL 脚本
echo "执行 SQL 脚本..."
sqlplus -S ${ORACLE_USER}/${ORACLE_PASSWORD}@${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_SID} <<EOF
SET SERVEROUTPUT ON
SET ECHO ON

-- 删除 finance_user 中的旧表
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE finance_user.log_mining_flush';
    DBMS_OUTPUT.PUT_LINE('✓ 已删除 finance_user.log_mining_flush');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
        DBMS_OUTPUT.PUT_LINE('✓ finance_user.log_mining_flush 不存在');
END;
/

-- 创建 SYSTEM 中的表
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE SYSTEM.log_mining_flush (scn NUMBER(19,0) NOT NULL, PRIMARY KEY (scn))';
    DBMS_OUTPUT.PUT_LINE('✓ 已创建 SYSTEM.log_mining_flush');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN -- ORA-00955: name is already used by an existing object
            DBMS_OUTPUT.PUT_LINE('✓ SYSTEM.log_mining_flush 已存在');
        ELSE
            RAISE;
        END IF;
END;
/

-- 授予权限
GRANT SELECT, INSERT, UPDATE, DELETE ON SYSTEM.log_mining_flush TO finance_user;

-- 验证
SELECT '✓ 表位置: ' || owner || '.' || table_name as status
FROM dba_tables 
WHERE table_name = 'LOG_MINING_FLUSH';

SELECT '✓ 权限: ' || grantee || ' - ' || privilege as status
FROM dba_tab_privs 
WHERE table_name = 'LOG_MINING_FLUSH'
ORDER BY grantee, privilege;

EXIT;
EOF

echo ""
echo "=========================================="
echo "✓ 修复完成"
echo "=========================================="
echo ""
echo "下一步:"
echo "1. 重新构建项目: mvn clean package -DskipTests"
echo "2. 重启 Flink 集群: docker-compose restart"
echo "3. 提交新的 CDC 任务"
echo ""
