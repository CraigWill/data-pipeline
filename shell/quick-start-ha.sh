#!/bin/bash
# Flink HA 快速启动脚本
# 快速启动已配置好的 HA 集群

set -e

echo "=========================================="
echo "Flink HA 快速启动"
echo "时间: $(date)"
echo "=========================================="
echo ""

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "错误: .env 文件不存在"
    exit 1
fi

# 检查 HA 配置
if ! grep -q "HA_MODE=zookeeper" .env; then
    echo "错误: HA 模式未启用"
    echo "请在 .env 中设置 HA_MODE=zookeeper"
    exit 1
fi

echo "启动 Flink HA 集群..."
echo ""

# 启动所有服务
echo "1. 启动 ZooKeeper..."
docker-compose up -d zookeeper
sleep 10

echo "2. 启动 JobManager（主和备）..."
docker-compose up -d jobmanager jobmanager-standby
sleep 30

echo "3. 启动 TaskManager..."
docker-compose up -d --scale taskmanager=3
sleep 15

echo ""
echo "=========================================="
echo "集群启动完成！"
echo ""

# 显示状态
docker-compose ps

echo ""
echo "访问信息:"
echo "  主 JobManager: http://localhost:8081"
echo "  备 JobManager: http://localhost:8082"
echo ""
echo "监控集群: ./shell/monitor-ha-cluster.sh"
echo "=========================================="
