#!/bin/bash

# Flink on Kubernetes 部署脚本
# 使用 Kubernetes 原生 HA，无需 ZooKeeper

set -e

echo "=========================================="
echo "Flink on Kubernetes 部署脚本"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 kubectl 是否安装
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}错误: kubectl 未安装${NC}"
    exit 1
fi

# 检查 Kubernetes 集群连接
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Kubernetes 集群连接正常${NC}"

# 1. 创建命名空间
echo ""
echo "步骤 1: 创建命名空间..."
kubectl apply -f namespace.yaml
echo -e "${GREEN}✓ 命名空间创建完成${NC}"

# 2. 创建 RBAC
echo ""
echo "步骤 2: 创建 RBAC 权限..."
kubectl apply -f flink-rbac.yaml
echo -e "${GREEN}✓ RBAC 权限创建完成${NC}"

# 3. 创建 Secret
echo ""
echo "步骤 3: 创建 Secret..."
if [ ! -f "flink-secrets.yaml" ]; then
    echo -e "${YELLOW}警告: flink-secrets.yaml 不存在${NC}"
    echo "请从 flink-secrets.yaml.example 复制并修改配置："
    echo "  cp flink-secrets.yaml.example flink-secrets.yaml"
    echo "  # 编辑 flink-secrets.yaml，填入真实的数据库密码等信息"
    echo ""
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    kubectl apply -f flink-secrets.yaml
    echo -e "${GREEN}✓ Secret 创建完成${NC}"
fi

# 4. 创建 PVC
echo ""
echo "步骤 4: 创建持久化存储..."
kubectl apply -f flink-pvc.yaml
echo -e "${GREEN}✓ PVC 创建完成${NC}"

# 5. 创建 ConfigMap
echo ""
echo "步骤 5: 创建 Flink 配置..."
kubectl apply -f flink-configuration-configmap.yaml
echo -e "${GREEN}✓ ConfigMap 创建完成${NC}"

# 6. 部署 JobManager
echo ""
echo "步骤 6: 部署 Flink JobManager..."
kubectl apply -f flink-jobmanager-service.yaml
kubectl apply -f flink-jobmanager-deployment.yaml
echo -e "${GREEN}✓ JobManager 部署完成${NC}"

# 7. 部署 TaskManager
echo ""
echo "步骤 7: 部署 Flink TaskManager..."
kubectl apply -f flink-taskmanager-deployment.yaml
echo -e "${GREEN}✓ TaskManager 部署完成${NC}"

# 8. 部署 Monitor Backend
echo ""
echo "步骤 8: 部署 Monitor Backend..."
kubectl apply -f monitor-backend-deployment.yaml
echo -e "${GREEN}✓ Monitor Backend 部署完成${NC}"

# 9. 部署 Monitor Frontend
echo ""
echo "步骤 9: 部署 Monitor Frontend..."
kubectl apply -f monitor-frontend-deployment.yaml
echo -e "${GREEN}✓ Monitor Frontend 部署完成${NC}"

# 10. 等待 Pod 就绪
echo ""
echo "步骤 10: 等待 Pod 就绪..."
echo "等待 JobManager..."
kubectl wait --for=condition=ready pod -l app=flink,component=jobmanager -n flink --timeout=300s
echo -e "${GREEN}✓ JobManager 就绪${NC}"

echo "等待 TaskManager..."
kubectl wait --for=condition=ready pod -l app=flink,component=taskmanager -n flink --timeout=300s
echo -e "${GREEN}✓ TaskManager 就绪${NC}"

echo "等待 Monitor Backend..."
kubectl wait --for=condition=ready pod -l app=monitor-backend -n flink --timeout=300s
echo -e "${GREEN}✓ Monitor Backend 就绪${NC}"

echo "等待 Monitor Frontend..."
kubectl wait --for=condition=ready pod -l app=monitor-frontend -n flink --timeout=300s
echo -e "${GREEN}✓ Monitor Frontend 就绪${NC}"

# 11. 显示部署状态
echo ""
echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo ""
echo "查看 Pod 状态:"
kubectl get pods -n flink
echo ""
echo "查看 Service:"
kubectl get svc -n flink
echo ""
echo "访问地址:"
echo "  Flink Web UI: kubectl port-forward -n flink svc/flink-jobmanager-rest 8081:8081"
echo "  然后访问: http://localhost:8081"
echo ""
echo "  Monitor Frontend: kubectl port-forward -n flink svc/monitor-frontend 8888:80"
echo "  然后访问: http://localhost:8888"
echo ""
echo "查看日志:"
echo "  JobManager: kubectl logs -n flink -l app=flink,component=jobmanager -f"
echo "  TaskManager: kubectl logs -n flink -l app=flink,component=taskmanager -f"
echo "  Monitor Backend: kubectl logs -n flink -l app=monitor-backend -f"
echo ""
echo "扩展 TaskManager:"
echo "  kubectl scale deployment flink-taskmanager -n flink --replicas=5"
echo ""
