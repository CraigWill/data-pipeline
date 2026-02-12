#!/bin/bash
# CDC 应用程序测试脚本

set -e

echo "=========================================="
echo "CDC 应用程序测试"
echo "=========================================="
echo ""

# 设置测试环境变量
export DATABASE_HOST=localhost
export DATABASE_PORT=1521
export DATABASE_USERNAME=finance_user
export DATABASE_PASSWORD=password
export DATABASE_SCHEMA=helowin
export DATABASE_TABLES=trans_info
export OUTPUT_PATH=./test-output/cdc
export POLL_INTERVAL_SECONDS=5

echo "测试配置:"
echo "  数据库: $DATABASE_HOST:$DATABASE_PORT"
echo "  Schema: $DATABASE_SCHEMA"
echo "  表: $DATABASE_TABLES"
echo "  输出: $OUTPUT_PATH"
echo ""

# 清理旧的测试输出
rm -rf "$OUTPUT_PATH"
mkdir -p "$OUTPUT_PATH"

# 检查 JAR 文件
JAR_FILE=$(ls target/realtime-data-pipeline-*.jar 2>/dev/null | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "❌ 找不到 JAR 文件"
    echo "请先运行: mvn clean package -DskipTests"
    exit 1
fi

echo "✅ 找到 JAR 文件: $JAR_FILE"
echo ""

echo "启动 CDC 应用程序（测试模式）..."
echo "应用程序将运行 30 秒后自动停止"
echo ""

# 在后台运行应用程序
timeout 30 java -cp "$JAR_FILE" \
    com.realtime.pipeline.JdbcCDCApp &

PID=$!
echo "应用程序 PID: $PID"
echo ""

# 等待应用程序启动
sleep 5

echo "检查输出文件..."
sleep 10

if [ -d "$OUTPUT_PATH" ] && [ "$(ls -A $OUTPUT_PATH 2>/dev/null)" ]; then
    echo "✅ 输出文件已创建"
    echo ""
    echo "输出文件列表:"
    ls -lh "$OUTPUT_PATH"
    echo ""
    echo "文件内容预览:"
    head -20 "$OUTPUT_PATH"/* 2>/dev/null || echo "（文件可能还在写入中）"
else
    echo "⚠️  输出文件尚未创建（可能需要更多时间）"
fi

echo ""
echo "等待应用程序完成..."
wait $PID 2>/dev/null || true

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
echo ""

if [ -d "$OUTPUT_PATH" ] && [ "$(ls -A $OUTPUT_PATH 2>/dev/null)" ]; then
    echo "✅ 测试成功！"
    echo ""
    echo "生成的文件:"
    ls -lh "$OUTPUT_PATH"
    echo ""
    echo "记录数:"
    wc -l "$OUTPUT_PATH"/* 2>/dev/null || echo "0"
    echo ""
    echo "查看完整输出:"
    echo "  cat $OUTPUT_PATH/*"
else
    echo "⚠️  未生成输出文件"
    echo "这可能是因为:"
    echo "  1. 应用程序启动时间较长"
    echo "  2. 数据库连接问题"
    echo "  3. 配置错误"
fi

echo ""
