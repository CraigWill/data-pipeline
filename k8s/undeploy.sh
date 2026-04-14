#!/bin/bash

# Flink on Kubernetes 卸载脚本

set -e

echo "=========================================="
echo "Flink on Kubernetes 卸载脚本"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 确认操作
echo -e "${YELLOW}警告: 此操作将删除所有 Flink 相关资源${NC}"
echo "包括："
echo "  - 所有 Pod"
echo "  - 所有 Service"
echo "  - 所有 Deployment"
echo "  - 所有 ConfigMap"
echo "  - 所有 Secret"
echo "  - 所有 PVC（数据将丢失）"
echo ""
read -p "确认删除？(yes/no) " -r
echo
if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "取消操作"
    exit 0
fi

# 删除资源
echo ""
echo "开始删除资源..."

echo "删除 Monitor Frontend..."
kubectl delete -f monitor-frontend-deployment.yaml --ignore-not-found=true

echo "删除 Monitor Backend..."
kubectl delete -f monitor-backend-deployment.yaml --ignore-not-found=true

echo "删除 TaskManager..."
kubectl delete -f flink-taskmanager-deployment.yaml --ignore-not-found=true

echo "删除 JobManager..."
kubectl delete -f flink-jobmanager-deployment.yaml --ignore-not-found=true
kubectl delete -f flink-jobmanager-service.yaml --ignore-not-found=true

echo "删除 ConfigMap..."
kubectl delete -f flink-configuration-configmap.yaml --ignore-not-found=true

echo "删除 PVC..."
kubectl delete -f flink-pvc.yaml --ignore-not-found=true

echo "删除 Secret..."
kubectl delete -f flink-secrets.yaml --ignore-not-found=true

echo "删除 RBAC..."
kubectl delete -f flink-rbac.yaml --ignore-not-found=true

# 询问是否删除命名空间
echo ""
read -p "是否删除命名空间 'flink'？(y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "删除命名空间..."
    kubectl delete -f namespace.yaml --ignore-not-found=true
    echo -e "${GREEN}✓ 命名空间已删除${NC}"
else
    echo "保留命名空间"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}卸载完成！${NC}"
echo "=========================================="
