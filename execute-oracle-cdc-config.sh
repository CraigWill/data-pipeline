#!/bin/bash
# 自动配置 Oracle 数据库以支持 CDC

set -e

ORACLE_CONTAINER="oracle11g"

echo "=========================================="
echo "Oracle CDC 自动配置脚本"
echo "=========================================="
echo ""
echo "Oracle 容器: $ORACLE_CONTAINER"
echo ""

# 检查容器是否运行
if ! docker ps | grep -q "$ORACLE_CONTAINER"; then
    echo "❌ Oracle 容器未运行"
    exit 1
fi

echo "✅ Oracle 容器正在运行"
echo ""

# 创建临时 SQL 文件
SQL_FILE=$(mktemp)
cat > "$SQL_FILE" << 'EOSQL'
-- 检查当前状态
SET PAGESIZE 100
SET LINESIZE 200
PROMPT ========================================
PROMPT 当前数据库状态
PROMPT ========================================
SELECT LOG_MODE FROM V$DATABASE;

PROMPT
PROMPT ========================================
PROMPT 启用归档日志模式
PROMPT ========================================

-- 关闭数据库
SHUTDOWN IMMEDIATE;

-- 启动到 mount 状态
STARTUP MOUNT;

-- 启用归档日志
ALTER DATABASE ARCHIVELOG;

-- 打开数据库
ALTER DATABASE OPEN;

PROMPT
PROMPT ========================================
PROMPT 启用补充日志
PROMPT ========================================

-- 启用补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

PROMPT
PROMPT ========================================
PROMPT 验证配置
PROMPT ========================================

PROMPT 归档日志模式:
SELECT LOG_MODE FROM V$DATABASE;

PROMPT
PROMPT 补充日志状态:
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL FROM V$DATABASE;

PROMPT
PROMPT ========================================
PROMPT 配置完成！
PROMPT ========================================

EXIT;
EOSQL

echo "📝 正在执行配置 SQL..."
echo ""

# 复制 SQL 文件到容器
docker cp "$SQL_FILE" "$ORACLE_CONTAINER:/tmp/configure_cdc.sql"

# 执行 SQL
docker exec -i "$ORACLE_CONTAINER" bash -c "
    export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
    export PATH=\$ORACLE_HOME/bin:\$PATH
    export ORACLE_SID=helowin
    sqlplus -S / as sysdba @/tmp/configure_cdc.sql
"

# 清理临时文件
rm -f "$SQL_FILE"
docker exec "$ORACLE_CONTAINER" rm -f /tmp/configure_cdc.sql

echo ""
echo "=========================================="
echo "✅ Oracle 配置完成！"
echo "=========================================="
echo ""
echo "正在等待 Oracle CDC 作业恢复..."
sleep 10

echo ""
echo "🔍 检查 CDC 作业状态..."
docker logs realtime-pipeline-taskmanager-1 2>&1 | tail -20 | grep -E "(Oracle|LogMiner|RUNNING|ERROR)" || echo "查看完整日志: docker logs realtime-pipeline-taskmanager-1"

echo ""
echo "=========================================="
echo "下一步"
echo "=========================================="
echo ""
echo "1. 查看 Flink Web UI:"
echo "   open http://localhost:8081"
echo ""
echo "2. 查看 TaskManager 日志:"
echo "   docker logs -f realtime-pipeline-taskmanager-1"
echo ""
echo "3. 等待约 30 秒，作业将自动恢复并开始捕获数据"
echo ""
echo "=========================================="
