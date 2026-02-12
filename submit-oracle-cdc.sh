#!/bin/bash
# 提交 Oracle CDC 作业到 Flink 集群

set -e

echo "=========================================="
echo "提交 Oracle CDC 作业到 Flink"
echo "=========================================="
echo ""

# 检查 JAR 文件是否存在
JAR_FILE="target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR 文件不存在: $JAR_FILE"
    echo "正在构建应用..."
    mvn clean package -DskipTests
    
    if [ ! -f "$JAR_FILE" ]; then
        echo "❌ 构建失败"
        exit 1
    fi
fi

# 检查 JobManager 是否运行
if ! docker ps | grep -q "flink-jobmanager"; then
    echo "❌ Flink JobManager 未运行"
    echo "请先启动: docker-compose up -d"
    exit 1
fi

echo "✅ 环境检查通过"
echo ""

# 复制 JAR 文件到 JobManager 容器
echo "📦 复制 JAR 文件到 JobManager..."
docker cp "$JAR_FILE" flink-jobmanager:/opt/flink/lib/
echo "✅ JAR 文件已复制"
echo ""

# 取消现有的作业（如果有）
echo "🔍 检查现有作业..."
EXISTING_JOBS=$(docker exec flink-jobmanager flink list -r 2>/dev/null | grep -E "RUNNING|RESTARTING" | awk '{print $4}' || true)

if [ -n "$EXISTING_JOBS" ]; then
    echo "⚠️  发现运行中的作业，正在取消..."
    for JOB_ID in $EXISTING_JOBS; do
        echo "  ⏹️  取消作业: $JOB_ID"
        docker exec flink-jobmanager flink cancel "$JOB_ID" 2>/dev/null || true
    done
    echo "⏳ 等待作业完全停止..."
    sleep 5
    echo "✅ 旧作业已停止"
else
    echo "✅ 没有运行中的作业"
fi
echo ""

# 提交新作业
echo "🚀 提交 Oracle CDC 作业..."
JOB_OUTPUT=$(docker exec flink-jobmanager flink run \
    -d \
    -c com.realtime.pipeline.OracleCDCApp \
    /opt/flink/lib/realtime-data-pipeline-1.0.0-SNAPSHOT.jar 2>&1)

echo "$JOB_OUTPUT"

# 提取作业 ID
JOB_ID=$(echo "$JOB_OUTPUT" | grep -oE "Job has been submitted with JobID [a-f0-9]+" | awk '{print $NF}' || true)

echo ""
echo "=========================================="
echo "✅ 作业提交成功！"
echo "=========================================="

if [ -n "$JOB_ID" ]; then
    echo "作业 ID: $JOB_ID"
fi

echo ""
echo "📊 Flink Web UI: http://localhost:8081"
echo ""
echo "📝 查看作业状态:"
echo "  docker exec flink-jobmanager flink list -r"
echo ""
echo "📋 查看 JobManager 日志:"
echo "  docker logs -f flink-jobmanager"
echo ""
echo "📋 查看 TaskManager 日志:"
echo "  docker logs -f realtime-pipeline-taskmanager-1"
echo ""
echo "📁 查看输出文件:"
echo "  docker exec realtime-pipeline-taskmanager-1 ls -la /opt/flink/output/cdc/"
echo ""
echo "💡 提示："
echo "  - LogMiner CDC 会输出 JSON 格式的变更事件"
echo "  - 可以捕获 INSERT/UPDATE/DELETE 所有操作"
echo "  - 延迟为秒级（比轮询方式快得多）"
echo ""
echo "=========================================="

# 等待几秒后检查作业状态
echo ""
echo "⏳ 等待作业启动..."
sleep 5

echo ""
echo "🔍 当前作业状态:"
docker exec flink-jobmanager flink list -r 2>/dev/null | grep -v "WARNING" || true

echo ""
echo "=========================================="
