#!/bin/bash
# 提交作业到 Flink 集群

set -e

echo "=========================================="
echo "提交作业到 Flink 集群"
echo "=========================================="
echo ""

# 检查 JAR 文件
JAR_FILE="target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR 文件不存在: $JAR_FILE"
    echo "正在构建..."
    mvn clean package -DskipTests
fi

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ JAR 文件: $JAR_FILE"
JAR_SIZE=$(ls -lh "$JAR_FILE" | awk '{print $5}')
echo "   大小: $JAR_SIZE"
echo ""

# 检查 Flink 集群
echo "检查 Flink 集群状态..."
if ! docker ps | grep -q flink-jobmanager; then
    echo "❌ Flink JobManager 未运行"
    echo ""
    echo "启动 Flink 集群:"
    echo "  docker compose up -d"
    exit 1
fi

echo "✅ Flink JobManager 运行中"
echo ""

# 选择要运行的主类
echo "可用的主类:"
echo "  1. SimpleCDCApp - 简化的 CDC 应用（使用项目类）"
echo "  2. JdbcCDCApp - JDBC CDC 应用（轮询模式）"
echo "  3. FlinkPipelineMain - 完整的管道应用"
echo ""

# 默认使用 SimpleCDCApp
MAIN_CLASS=${1:-com.realtime.pipeline.SimpleCDCApp}

if [ "$1" == "1" ] || [ "$1" == "simple" ]; then
    MAIN_CLASS="com.realtime.pipeline.SimpleCDCApp"
    APP_NAME="Simple CDC Application"
elif [ "$1" == "2" ] || [ "$1" == "jdbc" ]; then
    MAIN_CLASS="com.realtime.pipeline.JdbcCDCApp"
    APP_NAME="JDBC CDC Application"
elif [ "$1" == "3" ] || [ "$1" == "main" ]; then
    MAIN_CLASS="com.realtime.pipeline.FlinkPipelineMain"
    APP_NAME="Flink Pipeline Main"
else
    APP_NAME="Simple CDC Application"
fi

echo "主类: $MAIN_CLASS"
echo "应用: $APP_NAME"
echo ""

# 复制 JAR 到容器
echo "复制 JAR 文件到 JobManager 容器..."
docker cp "$JAR_FILE" flink-jobmanager:/opt/flink/lib/app.jar
echo "✅ JAR 文件已复制"
echo ""

# 提交作业
echo "提交作业到 Flink 集群..."
echo "----------------------------------------"
docker exec flink-jobmanager flink run \
    -c "$MAIN_CLASS" \
    /opt/flink/lib/app.jar

echo ""
echo "=========================================="
echo "作业已提交！"
echo "=========================================="
echo ""
echo "查看作业状态:"
echo "  Web UI: http://localhost:8081"
echo "  命令行: docker exec flink-jobmanager flink list"
echo ""
echo "查看日志:"
echo "  JobManager: docker logs -f flink-jobmanager"
echo "  TaskManager: docker logs -f realtime-pipeline-taskmanager-1"
echo ""
echo "取消作业:"
echo "  docker exec flink-jobmanager flink list"
echo "  docker exec flink-jobmanager flink cancel <job-id>"
echo ""
