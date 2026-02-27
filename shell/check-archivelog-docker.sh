#!/bin/bash

echo "=== 检查 Oracle 归档日志状态（通过 Docker）==="
echo ""

# 查找 Oracle 容器
echo "1. 查找 Oracle 容器..."
ORACLE_CONTAINER=$(docker ps --format "{{.Names}}" | grep -i oracle | head -1)

if [ -z "$ORACLE_CONTAINER" ]; then
    echo "❌ 未找到运行中的 Oracle 容器"
    echo ""
    echo "可用的容器:"
    docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
    exit 1
fi

echo "找到 Oracle 容器: $ORACLE_CONTAINER"
echo ""

# 检查归档日志状态
echo "2. 检查归档日志状态..."
docker exec $ORACLE_CONTAINER bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S / as sysdba <<EOF
SET PAGESIZE 100
SET LINESIZE 200

PROMPT ============================================
PROMPT 数据库归档日志模式
PROMPT ============================================
SELECT 
    name AS database_name,
    log_mode,
    CASE 
        WHEN log_mode = 'ARCHIVELOG' THEN '✓ 已启用'
        ELSE '✗ 未启用'
    END AS status
FROM v\$database;

PROMPT
PROMPT ============================================
PROMPT 归档日志配置
PROMPT ============================================
ARCHIVE LOG LIST;

PROMPT
PROMPT ============================================
PROMPT 当前 Redo Log 状态
PROMPT ============================================
SELECT 
    group#,
    thread#,
    sequence#,
    bytes / 1024 / 1024 AS size_mb,
    members,
    status,
    archived
FROM v\$log
ORDER BY group#;

EXIT;
EOF
"

echo ""
echo "=== 检查完成 ==="
