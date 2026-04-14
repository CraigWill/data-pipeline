#!/bin/bash

# Flink on Kubernetes 本地部署脚本
# 适用于 Docker Desktop、Minikube、Kind 等本地 K8s 环境

set -e

echo "=========================================="
echo "Flink on Kubernetes 本地部署脚本"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 kubectl 是否可用
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}错误: kubectl 未安装或不在 PATH 中${NC}"
    exit 1
fi

# 检查 Kubernetes 集群连接
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Kubernetes 集群连接正常${NC}"
echo ""

# 显示集群信息
echo "集群信息:"
kubectl cluster-info | head -2
echo ""

# 确认执行
read -p "是否继续执行本地部署？(y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "取消执行"
    exit 0
fi

# 1. 创建命名空间
echo ""
echo -e "${YELLOW}步骤 1: 创建命名空间...${NC}"
kubectl apply -f namespace.yaml
echo -e "${GREEN}✓ 命名空间创建完成${NC}"

# 2. 创建 RBAC
echo ""
echo -e "${YELLOW}步骤 2: 创建 RBAC 权限...${NC}"
kubectl apply -f flink-rbac.yaml
echo -e "${GREEN}✓ RBAC 权限创建完成${NC}"

# 3. 创建 ConfigMap
echo ""
echo -e "${YELLOW}步骤 3: 创建 Flink 配置...${NC}"
kubectl apply -f flink-configuration-configmap.yaml
echo -e "${GREEN}✓ ConfigMap 创建完成${NC}"

# 4. 部署 JobManager
echo ""
echo -e "${YELLOW}步骤 4: 部署 Flink JobManager...${NC}"
kubectl apply -f flink-jobmanager-service.yaml
kubectl apply -f flink-jobmanager-deployment-local.yaml
echo -e "${GREEN}✓ JobManager 部署完成${NC}"

# 5. 部署 TaskManager
echo ""
echo -e "${YELLOW}步骤 5: 部署 Flink TaskManager...${NC}"
kubectl apply -f flink-taskmanager-deployment-local.yaml
echo -e "${GREEN}✓ TaskManager 部署完成${NC}"

# 6. 部署 Monitor Backend
echo ""
echo -e "${YELLOW}步骤 6: 部署 Monitor Backend...${NC}"
kubectl apply -f monitor-backend-deployment-local.yaml
echo -e "${GREEN}✓ Monitor Backend 部署完成${NC}"

# 7. 部署 Monitor Frontend
echo ""
echo -e "${YELLOW}步骤 7: 部署 Monitor Frontend...${NC}"
kubectl apply -f monitor-frontend-deployment-local.yaml
echo -e "${GREEN}✓ Monitor Frontend 部署完成${NC}"

# 8. 等待 Pod 就绪
echo ""
echo -e "${YELLOW}步骤 8: 等待 Pod 就绪...${NC}"
echo "等待 JobManager..."
kubectl wait --for=condition=ready pod -l app=flink,component=jobmanager -n flink --timeout=300s || true
echo -e "${GREEN}✓ JobManager 就绪${NC}"

echo "等待 TaskManager..."
kubectl wait --for=condition=ready pod -l app=flink,component=taskmanager -n flink --timeout=300s || true
echo -e "${GREEN}✓ TaskManager 就绪${NC}"

echo "等待 Monitor Backend..."
kubectl wait --for=condition=ready pod -l app=monitor-backend -n flink --timeout=300s || true
echo -e "${GREEN}✓ Monitor Backend 就绪${NC}"

echo "等待 Monitor Frontend..."
kubectl wait --for=condition=ready pod -l app=monitor-frontend -n flink --timeout=300s || true
echo -e "${GREEN}✓ Monitor Frontend 就绪${NC}"

# 9. 显示部署状态
echo ""
echo "=========================================="
echo -e "${GREEN}部署完成！${NC}"
echo "=========================================="
echo ""
echo "查看 Pod 状态:"
kubectl get pods -n flink
echo ""
echo "查看 Service:"
kubectl get svc -n flink
echo ""
echo "访问地址:"
echo "  Flink Web UI:"
echo "    kubectl port-forward -n flink svc/flink-jobmanager-rest 8081:8081"
echo "    然后访问: http://localhost:8081"
echo ""
echo "  Monitor Frontend:"
echo "    kubectl port-forward -n flink svc/monitor-frontend 8888:80"
echo "    然后访问: http://localhost:8888"
echo ""
echo "  或使用 NodePort (如果支持):"
echo "    Monitor Frontend: http://localhost:30888"
echo ""
echo "查看日志:"
echo "  JobManager: kubectl logs -n flink -l app=flink,component=jobmanager -f"
echo "  TaskManager: kubectl logs -n flink -l app=flink,component=taskmanager -f"
echo "  Monitor Backend: kubectl logs -n flink -l app=monitor-backend -f"
echo ""
echo "扩展 TaskManager:"
echo "  kubectl scale deployment flink-taskmanager -n flink --replicas=3"
echo ""
