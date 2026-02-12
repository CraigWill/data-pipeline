#!/bin/bash
# 启动简化的 CDC 应用程序（使用项目类）

set -e

echo "=========================================="
echo "简化 CDC 应用程序"
echo "=========================================="
echo ""

# 加载环境变量
if [ -f .env ]; then
    source <(grep -v '^#' .env | grep -v '^$' | sed 's/^/export /')
fi

# 设置默认值
export DATABASE_HOST=${DATABASE_HOST:-localhost}
export DATABASE_PORT=${DATABASE_PORT:-1521}
export DATABASE_USERNAME=${DATABASE_USERNAME:-finance_user}
export DATABASE_PASSWORD=${DATABASE_PASSWORD:-password}
export DATABASE_SCHEMA=${DATABASE_SCHEMA:-helowin}
export DATABASE_TABLES=${DATABASE_TABLES:-trans_info}
export OUTPUT_PATH=${OUTPUT_PATH:-./output/cdc}

echo "配置:"
echo "  数据库: $DATABASE_HOST:$DATABASE_PORT"
echo "  Schema: $DATABASE_SCHEMA"
echo "  表: $DATABASE_TABLES"
echo "  输出: $OUTPUT_PATH"
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
echo "特性:"
echo "  ✅ 使用 DatabaseConfig 配置类"
echo "  ✅ 使用 OutputConfig 配置类"
echo "  ✅ 生成 ChangeEvent 模型对象"
echo "  ✅ 转换为 ProcessedEvent 模型对象"
echo "  ✅ 输出 CSV 格式文件"
echo ""
echo "启动应用程序..."
echo "输出文件将保存到: $OUTPUT_PATH"
echo "按 Ctrl+C 停止"
echo ""
echo "=========================================="
echo ""

# 运行应用程序
java -cp "$JAR_FILE" \
    com.realtime.pipeline.SimpleCDCApp
