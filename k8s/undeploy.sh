#!/bin/bash

# Flink on Kubernetes 卸载脚本
# 支持本地和生产环境

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "=========================================="
echo "Flink on Kubernetes 卸载脚本"
echo "=========================================="

# 确认操作
echo -e "${YELLOW}警告: 此操作将删除 flink 命名空间中的所有资源${NC}"
echo ""
read -p "确认删除？(yes/no) " -r
echo
if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "取消操作"
    exit 0
fi

cd "$SCRIPT_DIR"

echo ""
echo "删除 Deployments..."
kubectl delete deployment flink-jobmanager flink-taskmanager monitor-backend monitor-frontend \
    -n flink --ignore-not-found=true 2>/dev/null || true

echo "删除 Services..."
kubectl delete -f flink-jobmanager-service.yaml --ignore-not-found=true 2>/dev/null || true
kubectl delete svc monitor-backend monitor-frontend -n flink --ignore-not-found=true 2>/dev/null || true

echo "删除 ConfigMaps..."
kubectl delete -f flink-configuration-configmap.yaml --ignore-not-found=true 2>/dev/null || true
kubectl delete configmap flink-cluster-cluster-config-map -n flink --ignore-not-found=true 2>/dev/null || true

echo "删除 PVC..."
kubectl delete -f flink-pvc.yaml --ignore-not-found=true 2>/dev/null || true
kubectl delete -f flink-pvc-local.yaml --ignore-not-found=true 2>/dev/null || true

echo "删除 Secrets..."
kubectl delete -f flink-secrets.yaml --ignore-not-found=true 2>/dev/null || true

echo "删除 RBAC..."
kubectl delete -f flink-rbac.yaml --ignore-not-found=true 2>/dev/null || true

echo "删除 HPA..."
kubectl delete -f flink-hpa.yaml --ignore-not-found=true 2>/dev/null || true

# 询问是否删除命名空间
echo ""
read -p "是否删除命名空间 'flink'？(y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    kubectl delete -f namespace.yaml --ignore-not-found=true 2>/dev/null || true
    echo -e "${GREEN}✓ 命名空间已删除${NC}"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}卸载完成${NC}"
echo "=========================================="
