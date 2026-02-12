#!/bin/bash
# CDC 应用程序启动脚本

set -e

echo "=========================================="
echo "CDC 应用程序启动脚本"
echo "=========================================="
echo ""

# 加载环境变量
if [ -f .env ]; then
    echo "加载 .env 配置文件..."
    export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
    echo "✅ 配置已加载"
else
    echo "❌ 错误: .env 文件不存在"
    exit 1
fi

echo ""
echo "配置信息:"
echo "  数据库主机: ${DATABASE_HOST}"
echo "  数据库端口: ${DATABASE_PORT}"
echo "  数据库用户: ${DATABASE_USERNAME}"
echo "  Schema: ${DATABASE_SCHEMA}"
echo "  监控表: ${DATABASE_TABLES}"
echo ""

# 检查 JAR 文件
JAR_FILE=$(ls target/realtime-data-pipeline-*.jar 2>/dev/null | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "❌ 错误: 找不到应用程序 JAR 文件"
    echo "请先运行: mvn clean package"
    exit 1
fi

echo "找到 JAR 文件: $JAR_FILE"
echo ""

# 创建输出目录
OUTPUT_DIR="${OUTPUT_PATH:-./output/cdc}"
mkdir -p "$OUTPUT_DIR"
echo "输出目录: $OUTPUT_DIR"
echo ""

# 选择运行模式
echo "选择运行模式:"
echo "  1) 本地运行（直接运行）"
echo "  2) 提交到 Flink 集群"
echo ""
read -p "请选择 (1 或 2): " MODE

if [ "$MODE" = "1" ]; then
    echo ""
    echo "=========================================="
    echo "本地运行模式"
    echo "=========================================="
    echo ""
    
    java -cp "$JAR_FILE" \
        -Dlog4j.configuration=file:src/main/resources/log4j2.xml \
        com.realtime.pipeline.SimpleCDCApp
        
elif [ "$MODE" = "2" ]; then
    echo ""
    echo "=========================================="
    echo "提交到 Flink 集群"
    echo "=========================================="
    echo ""
    
    # 检查 Flink 集群是否运行
    if ! curl -s http://localhost:8081/overview > /dev/null 2>&1; then
        echo "❌ 错误: Flink 集群未运行"
        echo "请先启动 Flink 集群: docker compose up -d"
        exit 1
    fi
    
    echo "✅ Flink 集群正在运行"
    echo ""
    
    # 提交作业到 Flink
    docker exec flink-jobmanager flink run \
        -c com.realtime.pipeline.SimpleCDCApp \
        /opt/flink/lib/realtime-data-pipeline.jar
        
    echo ""
    echo "作业已提交！"
    echo "访问 Flink Web UI 查看作业状态: http://localhost:8081"
    
else
    echo "无效的选择"
    exit 1
fi

echo ""
echo "=========================================="
echo "CDC 应用程序已启动"
echo "=========================================="
echo ""
echo "输出文件将保存到: $OUTPUT_DIR"
echo "按 Ctrl+C 停止应用程序"
echo ""
