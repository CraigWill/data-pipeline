#!/bin/bash

# 完全重置 Flink 集群
# 警告：这将删除所有 checkpoint 和作业状态

set -e

echo "=========================================="
echo "⚠️  Flink 集群完全重置"
echo "=========================================="
echo ""
echo "此操作将："
echo "1. 停止所有 Flink 组件"
echo "2. 清理所有 checkpoint 数据"
echo "3. 清理所有 savepoint 数据"
echo "4. 重启集群"
echo ""
echo "⚠️  警告: 所有作业将从头开始处理数据！"
echo ""

read -p "确认继续? (yes/NO) " -r
if [[ ! $REPLY =~ ^yes$ ]]; then
    echo "已取消"
    exit 0
fi

echo ""
echo "步骤 1: 停止所有 Flink 组件..."
docker-compose stop jobmanager jobmanager-standby taskmanager

echo ""
echo "步骤 2: 清理 checkpoint 数据..."
docker exec flink-jobmanager rm -rf /opt/flink/checkpoints/* 2>/dev/null || echo "Checkpoint 目录已清空"

echo ""
echo "步骤 3: 清理 savepoint 数据..."
docker exec flink-jobmanager rm -rf /opt/flink/savepoints/* 2>/dev/null || echo "Savepoint 目录已清空"

echo ""
echo "步骤 4: 重启 Flink 集群..."
docker-compose up -d jobmanager jobmanager-standby taskmanager

echo ""
echo "步骤 5: 等待集群就绪..."
sleep 30

echo ""
echo "步骤 6: 检查集群状态..."
for i in {1..10}; do
    STATUS=$(curl -s http://localhost:8081/overview 2>/dev/null || echo "")
    if echo "$STATUS" | grep -q "taskmanagers"; then
        echo "✅ 集群已就绪"
        curl -s http://localhost:8081/overview | python3 -m json.tool
        break
    else
        echo "等待集群就绪... ($i/10)"
        sleep 5
    fi
done

echo ""
echo "=========================================="
echo "重置完成"
echo "=========================================="
echo ""
echo "后续步骤:"
echo "1. 访问 Flink Web UI: http://localhost:8081"
echo "2. 通过前端重新提交所有 CDC 任务"
echo "3. 监控作业运行状态"
echo ""
