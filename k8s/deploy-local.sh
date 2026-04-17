#!/bin/bash

# Flink on Kubernetes 本地一键部署脚本
# 适用于 Docker Desktop (Kind)、Minikube 等本地 K8s 环境
#
# 功能：构建镜像 → 加载到集群 → 部署所有组件 → 等待就绪
#
# 用法:
#   ./deploy-local.sh              # 完整部署（构建+部署）
#   ./deploy-local.sh --skip-build # 跳过构建，仅部署
#   ./deploy-local.sh --build-only # 仅构建镜像，不部署
#   ./deploy-local.sh --redeploy   # 删除旧部署后重新部署

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 镜像版本标签（基于时间戳，避免缓存问题）
IMAGE_TAG="local-$(date +%Y%m%d%H%M%S)"

# 参数解析
SKIP_BUILD=false
BUILD_ONLY=false
REDEPLOY=false
AUTO_YES=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build) SKIP_BUILD=true; shift ;;
        --build-only) BUILD_ONLY=true; shift ;;
        --redeploy)   REDEPLOY=true; shift ;;
        -y|--yes)     AUTO_YES=true; shift ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo "  --skip-build  跳过镜像构建"
            echo "  --build-only  仅构建镜像"
            echo "  --redeploy    删除旧部署后重新部署"
            echo "  -y, --yes     跳过确认提示"
            echo "  -h, --help    显示帮助"
            exit 0 ;;
        *) echo -e "${RED}未知参数: $1${NC}"; exit 1 ;;
    esac
done

echo "=========================================="
echo "Flink on Kubernetes 本地部署"
echo "镜像标签: ${IMAGE_TAG}"
echo "=========================================="

# ==========================================
# 前置检查
# ==========================================
echo ""
echo -e "${BLUE}>>> 前置检查${NC}"

# 检查 kubectl
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}错误: kubectl 未安装${NC}"
    exit 1
fi

# 检查集群连接
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"
    exit 1
fi
echo -e "${GREEN}✓ K8s 集群连接正常${NC}"

# 检查 Docker
if ! docker info &> /dev/null; then
    echo -e "${RED}错误: Docker 未运行${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker 运行正常${NC}"

# 检测 Kind 节点（Docker Desktop 内置 K8s 也使用 Kind）
KIND_NODE=$(docker ps --format '{{.Names}}' | grep -E "control-plane|kind" | head -1 || true)
if [ -n "$KIND_NODE" ]; then
    echo -e "${GREEN}✓ 检测到 Kind 节点: ${KIND_NODE}${NC}"
else
    echo -e "${YELLOW}⚠ 未检测到 Kind 节点，镜像将依赖 Docker Desktop 自动同步${NC}"
fi

# 确认执行
if [ "$AUTO_YES" = false ]; then
    echo ""
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "取消执行"
        exit 0
    fi
fi

# ==========================================
# 函数定义
# ==========================================

# 加载镜像到 Kind 集群的 containerd
load_image_to_cluster() {
    local image_name=$1
    local image_tag=$2

    if [ -n "$KIND_NODE" ]; then
        echo "  加载 ${image_name}:${image_tag} 到 ${KIND_NODE}..."
        docker save "${image_name}:${image_tag}" | \
            docker exec -i "$KIND_NODE" ctr -n k8s.io images import --all-platforms - 2>/dev/null
    elif command -v kind &> /dev/null; then
        kind load docker-image "${image_name}:${image_tag}"
    fi
}

# 等待 Pod 就绪，失败时打印日志
wait_for_pod() {
    local label=$1
    local name=$2
    local timeout=${3:-180s}

    echo -n "  等待 ${name}..."
    if kubectl wait --for=condition=ready pod -l "$label" -n flink --timeout="$timeout" &>/dev/null; then
        echo -e " ${GREEN}✓${NC}"
    else
        echo -e " ${RED}✗${NC}"
        echo -e "${RED}  ${name} 未能就绪，最近日志:${NC}"
        kubectl logs -n flink -l "$label" --tail=20 2>/dev/null || true
        return 1
    fi
}

# ==========================================
# 步骤 1: 构建镜像
# ==========================================
if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo -e "${BLUE}>>> 步骤 1: 构建 Docker 镜像${NC}"

    # 检查 JAR 文件
    if ! ls "$PROJECT_ROOT"/monitor-backend/target/monitor-backend-*-SNAPSHOT.jar &>/dev/null; then
        echo -e "${YELLOW}  未找到 backend JAR，开始 Maven 构建...${NC}"
        mvn -f "$PROJECT_ROOT/pom.xml" clean package -DskipTests -q
    fi

    if ! ls "$PROJECT_ROOT"/flink-jobs/target/flink-jobs-*-SNAPSHOT.jar &>/dev/null; then
        echo -e "${YELLOW}  未找到 flink-jobs JAR，开始 Maven 构建...${NC}"
        mvn -f "$PROJECT_ROOT/pom.xml" clean package -DskipTests -q
    fi

    # 构建 JobManager
    echo -e "  构建 flink-jobmanager:${IMAGE_TAG}..."
    docker build -f "$PROJECT_ROOT/docker/jobmanager/Dockerfile" \
        -t "flink-jobmanager:${IMAGE_TAG}" "$PROJECT_ROOT" -q
    echo -e "  ${GREEN}✓ JobManager${NC}"

    # 构建 TaskManager
    echo -e "  构建 flink-taskmanager:${IMAGE_TAG}..."
    docker build -f "$PROJECT_ROOT/docker/taskmanager/Dockerfile" \
        -t "flink-taskmanager:${IMAGE_TAG}" "$PROJECT_ROOT" -q
    echo -e "  ${GREEN}✓ TaskManager${NC}"

    # 构建 Monitor Backend
    echo -e "  构建 monitor-backend:${IMAGE_TAG}..."
    docker build -f "$PROJECT_ROOT/monitor-backend/Dockerfile" \
        -t "monitor-backend:${IMAGE_TAG}" "$PROJECT_ROOT" -q
    echo -e "  ${GREEN}✓ Monitor Backend${NC}"

    # 构建 Monitor Frontend
    echo -e "  构建 monitor-frontend:${IMAGE_TAG}..."
    docker build -f "$PROJECT_ROOT/monitor/frontend-vue/Dockerfile" \
        -t "monitor-frontend:${IMAGE_TAG}" "$PROJECT_ROOT/monitor/frontend-vue/" -q
    echo -e "  ${GREEN}✓ Monitor Frontend${NC}"

    # 加载镜像到集群
    echo ""
    echo -e "${BLUE}>>> 步骤 2: 加载镜像到 K8s 集群${NC}"
    load_image_to_cluster "flink-jobmanager" "$IMAGE_TAG"
    load_image_to_cluster "flink-taskmanager" "$IMAGE_TAG"
    load_image_to_cluster "monitor-backend" "$IMAGE_TAG"
    load_image_to_cluster "monitor-frontend" "$IMAGE_TAG"
    echo -e "  ${GREEN}✓ 镜像加载完成${NC}"

    if [ "$BUILD_ONLY" = true ]; then
        echo ""
        echo -e "${GREEN}镜像构建完成，标签: ${IMAGE_TAG}${NC}"
        exit 0
    fi
else
    echo ""
    echo -e "${YELLOW}>>> 跳过镜像构建${NC}"
    # 使用最新的本地镜像
    IMAGE_TAG="latest"
fi

# ==========================================
# 步骤 3: 删除旧部署（如果 --redeploy）
# ==========================================
if [ "$REDEPLOY" = true ]; then
    echo ""
    echo -e "${BLUE}>>> 步骤 3: 清理旧部署${NC}"
    kubectl delete deployment flink-jobmanager flink-taskmanager monitor-backend monitor-frontend \
        -n flink --ignore-not-found=true 2>/dev/null
    # 清理 Flink HA leader election ConfigMap
    kubectl delete configmap -n flink -l app=flink --ignore-not-found=true 2>/dev/null || true
    kubectl delete configmap flink-cluster-cluster-config-map -n flink --ignore-not-found=true 2>/dev/null || true
    echo -e "  ${GREEN}✓ 旧部署已清理${NC}"
fi

# ==========================================
# 步骤 4: 部署 K8s 资源
# ==========================================
echo ""
echo -e "${BLUE}>>> 步骤 4: 部署 K8s 资源${NC}"

cd "$SCRIPT_DIR"

# 基础资源
echo -e "  创建命名空间和 RBAC..."
kubectl apply -f namespace.yaml -q 2>/dev/null || kubectl apply -f namespace.yaml
kubectl apply -f flink-rbac.yaml -q 2>/dev/null || kubectl apply -f flink-rbac.yaml
echo -e "  ${GREEN}✓ 命名空间和 RBAC${NC}"

# ConfigMap
echo -e "  创建 Flink 配置..."
kubectl apply -f flink-configuration-configmap.yaml
echo -e "  ${GREEN}✓ ConfigMap${NC}"

# 使用 sed 替换镜像标签后部署（不修改原始文件）
deploy_with_tag() {
    local file=$1
    local old_image=$2
    local new_image=$3
    sed "s|image: ${old_image}:.*|image: ${new_image}:${IMAGE_TAG}|g" "$file" | kubectl apply -f -
}

# JobManager
echo -e "  部署 JobManager..."
kubectl apply -f flink-jobmanager-service.yaml
deploy_with_tag flink-jobmanager-deployment-local.yaml "flink-jobmanager" "flink-jobmanager"
echo -e "  ${GREEN}✓ JobManager${NC}"

# TaskManager
echo -e "  部署 TaskManager..."
deploy_with_tag flink-taskmanager-deployment-local.yaml "flink-taskmanager" "flink-taskmanager"
echo -e "  ${GREEN}✓ TaskManager${NC}"

# Monitor Backend
echo -e "  部署 Monitor Backend..."
deploy_with_tag monitor-backend-deployment-local.yaml "monitor-backend" "monitor-backend"
echo -e "  ${GREEN}✓ Monitor Backend${NC}"

# Monitor Frontend
echo -e "  部署 Monitor Frontend..."
deploy_with_tag monitor-frontend-deployment-local.yaml "monitor-frontend" "monitor-frontend"
echo -e "  ${GREEN}✓ Monitor Frontend${NC}"

# ==========================================
# 步骤 5: 等待 Pod 就绪
# ==========================================
echo ""
echo -e "${BLUE}>>> 步骤 5: 等待 Pod 就绪${NC}"

FAILED=0
wait_for_pod "app=flink,component=jobmanager" "JobManager" "180s" || FAILED=$((FAILED+1))
wait_for_pod "app=flink,component=taskmanager" "TaskManager" "180s" || FAILED=$((FAILED+1))
wait_for_pod "app=monitor-backend" "Monitor Backend" "120s" || FAILED=$((FAILED+1))
wait_for_pod "app=monitor-frontend" "Monitor Frontend" "60s" || FAILED=$((FAILED+1))

# ==========================================
# 完成
# ==========================================
echo ""
echo "=========================================="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}部署完成！所有组件就绪${NC}"
else
    echo -e "${YELLOW}部署完成，${FAILED} 个组件未就绪${NC}"
fi
echo "=========================================="
echo ""
echo "Pod 状态:"
kubectl get pods -n flink -o wide
echo ""
echo "Service:"
kubectl get svc -n flink
echo ""
echo "访问地址:"
echo "  Flink Web UI:     kubectl port-forward -n flink svc/flink-jobmanager-rest 8081:8081"
echo "  Monitor Frontend: kubectl port-forward -n flink svc/monitor-frontend 8888:80"
echo "  Monitor Backend:  kubectl port-forward -n flink svc/monitor-backend 5001:5001"
echo "  或 NodePort:      http://localhost:30888"
echo ""
echo "常用命令:"
echo "  查看日志:   kubectl logs -n flink -l app=flink,component=jobmanager -f"
echo "  扩展 TM:   kubectl scale deployment flink-taskmanager -n flink --replicas=3"
echo "  重新部署:   $0 --redeploy"
echo "  卸载:       $(dirname $0)/undeploy.sh"
echo ""
