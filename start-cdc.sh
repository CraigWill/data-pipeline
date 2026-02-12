#!/bin/bash
# 简化的 CDC 启动脚本

set -e

echo "=========================================="
echo "CDC 应用程序"
echo "=========================================="
echo ""

# 加载环境变量
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
fi

# 设置默认值
export DATABASE_HOST=${DATABASE_HOST:-localhost}
export DATABASE_PORT=${DATABASE_PORT:-1521}
export DATABASE_USERNAME=${DATABASE_USERNAME:-finance_user}
export DATABASE_PASSWORD=${DATABASE_PASSWORD:-password}
export DATABASE_SCHEMA=${DATABASE_SCHEMA:-helowin}
export DATABASE_TABLES=${DATABASE_TABLES:-trans_info}
export OUTPUT_PATH=${OUTPUT_PATH:-./output/cdc}
export POLL_INTERVAL_SECONDS=${POLL_INTERVAL_SECONDS:-10}

echo "配置:"
echo "  数据库: $DATABASE_HOST:$DATABASE_PORT"
echo "  Schema: $DATABASE_SCHEMA"
echo "  表: $DATABASE_TABLES"
echo "  输出: $OUTPUT_PATH"
echo "  轮询间隔: ${POLL_INTERVAL_SECONDS}秒"
echo ""

# 创建输出目录
mkdir -p "$OUTPUT_PATH"

# 检查 JAR 文件
JAR_FILE=$(ls target/realtime-data-pipeline-*.jar 2>/dev/null | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "❌ 找不到 JAR 文件，正在构建..."
    mvn clean package -DskipTests
    JAR_FILE=$(ls target/realtime-data-pipeline-*.jar 2>/dev/null | head -n 1)
fi

echo "使用 JAR: $JAR_FILE"
echo ""
echo "启动 CDC 应用程序..."
echo "输出文件将保存到: $OUTPUT_PATH"
echo "按 Ctrl+C 停止"
echo ""
echo "=========================================="
echo ""

# 运行应用程序
java -cp "$JAR_FILE" \
    com.realtime.pipeline.JdbcCDCApp

