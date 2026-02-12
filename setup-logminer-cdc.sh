#!/bin/bash
# Oracle LogMiner CDC 配置向导

set -e

echo "=========================================="
echo "Oracle LogMiner CDC 配置向导"
echo "=========================================="
echo ""

# 读取环境变量
source .env 2>/dev/null || true

DB_HOST="${DATABASE_HOST:-localhost}"
DB_PORT="${DATABASE_PORT:-1521}"
DB_SID="${DATABASE_SID:-helowin}"
DB_USER="${DATABASE_USERNAME:-system}"
DB_PASSWORD="${DATABASE_PASSWORD:-password}"

echo "数据库信息:"
echo "  主机: ${DB_HOST}"
echo "  端口: ${DB_PORT}"
echo "  SID: ${DB_SID}"
echo "  用户: ${DB_USER}"
echo ""

# 检查数据库连接
echo "正在测试数据库连接..."
if nc -z -w 5 localhost ${DB_PORT} 2>/dev/null; then
    echo "✅ 数据库端口可访问"
else
    echo "❌ 无法连接到数据库端口 ${DB_PORT}"
    exit 1
fi

echo ""
echo "=========================================="
echo "配置步骤"
echo "=========================================="
echo ""
echo "要启用 LogMiner CDC，需要执行以下步骤："
echo ""
echo "1. 启用补充日志（可以在线执行，不需要重启）"
echo "2. 启用归档日志（需要重启数据库）"
echo ""
echo "我们提供了两种配置方式："
echo ""
echo "方式 A: 手动配置（推荐）"
echo "  - 你需要手动连接到数据库"
echo "  - 执行提供的 SQL 脚本"
echo ""
echo "方式 B: 仅启用补充日志（不需要重启）"
echo "  - 如果归档日志已经启用"
echo "  - 只需要启用补充日志"
echo ""

read -p "请选择配置方式 (A/B) [A]: " CHOICE
CHOICE=${CHOICE:-A}

if [ "$CHOICE" = "A" ] || [ "$CHOICE" = "a" ]; then
    echo ""
    echo "=========================================="
    echo "方式 A: 手动配置"
    echo "=========================================="
    echo ""
    echo "请按照以下步骤操作："
    echo ""
    echo "步骤 1: 连接到 Oracle 数据库"
    echo ""
    echo "如果你的 Oracle 在 Docker 容器中，运行："
    echo "  docker ps | grep oracle"
    echo "  docker exec -it <container-name> bash"
    echo "  sqlplus / as sysdba"
    echo ""
    echo "如果你的 Oracle 在本地或远程服务器，运行："
    echo "  sqlplus ${DB_USER}/${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_SID} as sysdba"
    echo ""
    echo "步骤 2: 执行配置脚本"
    echo "  @configure-oracle-for-cdc.sql"
    echo ""
    echo "或者手动执行以下 SQL："
    echo ""
    cat configure-oracle-for-cdc.sql | grep -A 100 "步骤 2" | grep -B 100 "步骤 3" | grep "ALTER DATABASE"
    echo ""
    echo "步骤 3: 如果需要启用归档日志（需要重启）"
    echo "  SHUTDOWN IMMEDIATE;"
    echo "  STARTUP MOUNT;"
    echo "  ALTER DATABASE ARCHIVELOG;"
    echo "  ALTER DATABASE OPEN;"
    echo ""
    
    read -p "配置完成后，按 Enter 继续部署 CDC 应用..."
    
elif [ "$CHOICE" = "B" ] || [ "$CHOICE" = "b" ]; then
    echo ""
    echo "=========================================="
    echo "方式 B: 仅启用补充日志"
    echo "=========================================="
    echo ""
    echo "⚠️  注意：此方式假设归档日志已经启用"
    echo ""
    echo "将执行以下 SQL："
    echo "  ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;"
    echo ""
    
    read -p "是否继续? (y/n) [y]: " CONFIRM
    CONFIRM=${CONFIRM:-y}
    
    if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
        echo "已取消"
        exit 0
    fi
    
    echo ""
    echo "请手动连接到数据库并执行："
    echo "  ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;"
    echo ""
    
    read -p "执行完成后，按 Enter 继续..."
fi

echo ""
echo "=========================================="
echo "部署 LogMiner CDC 应用"
echo "=========================================="
echo ""

# 检查 JAR 文件
if [ ! -f "target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar" ]; then
    echo "正在构建应用..."
    mvn clean package -DskipTests
fi

# 检查 Docker 服务
if ! docker ps | grep -q "flink-jobmanager"; then
    echo "❌ Flink JobManager 未运行"
    echo "请先启动: docker-compose up -d"
    exit 1
fi

echo "✅ 准备就绪"
echo ""

read -p "是否现在部署 Oracle CDC 应用? (y/n) [y]: " DEPLOY
DEPLOY=${DEPLOY:-y}

if [ "$DEPLOY" = "y" ] || [ "$DEPLOY" = "Y" ]; then
    echo ""
    echo "正在部署..."
    ./submit-oracle-cdc.sh
else
    echo ""
    echo "稍后可以运行以下命令部署:"
    echo "  ./submit-oracle-cdc.sh"
fi

echo ""
echo "=========================================="
echo "完成！"
echo "=========================================="
