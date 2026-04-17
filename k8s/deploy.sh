#!/bin/bash

# Flink on Kubernetes 部署脚本
# 自动检测环境：本地 (Kind/Docker Desktop) 或生产
#
# 用法:
#   ./deploy.sh              # 自动检测环境并部署
#   ./deploy.sh --local      # 强制使用本地配置
#   ./deploy.sh --prod       # 强制使用生产配置
#   ./deploy.sh --redeploy   # 清理后重新部署
#   ./deploy.sh -y           # 跳过确认

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 参数
MODE=""
REDEPLOY=false
AUTO_YES=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --local)    MODE="local"; shift ;;
        --prod)     MODE="prod"; shift ;;
        --redeploy) REDEPLOY=true; shift ;;
        -y|--yes)   AUTO_YES=true; shift ;;
        -h|--help)
            echo "用法: $0 [--local|--prod] [--redeploy] [-y]"
            echo "  --local      使用本地配置 (hostPath, 无 Secret)"
            echo "  --prod       使用生产配置 (PVC, Secret)"
            echo "  --redeploy   清理旧部署后重新部署"
            echo "  -y           跳过确认"
            exit 0 ;;
        *) echo -e "${RED}未知参数: $1${NC}"; exit 1 ;;
    esac
done

echo "=========================================="
echo "Flink on Kubernetes 部署脚本"
echo "=========================================="

# ==========================================
# 前置检查
# ==========================================
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}错误: kubectl 未安装${NC}"
    exit 1
fi

if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"
    exit 1
fi
echo -e "${GREEN}✓ K8s 集群连接正常${NC}"

# ==========================================
# 自动检测环境
# ==========================================
if [ -z "$MODE" ]; then
    # 检测 Kind 节点
    KIND_NODE=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E "control-plane|kind" | head -1 || true)
    # 检测 Docker Desktop K8s
    CONTEXT=$(kubectl config current-context 2>/dev/null || true)

    if [ -n "$KIND_NODE" ] || [[ "$CONTEXT" == *"docker-desktop"* ]] || [[ "$CONTEXT" == *"minikube"* ]]; then
        MODE="local"
        echo -e "${YELLOW}检测到本地环境 (${CONTEXT})，使用 local 配置${NC}"
    else
        MODE="prod"
        echo -e "${BLUE}使用生产配置${NC}"
    fi
fi

echo "部署模式: ${MODE}"

# 确认
if [ "$AUTO_YES" = false ]; then
    echo ""
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    [[ ! $REPLY =~ ^[Yy]$ ]] && echo "取消" && exit 0
fi

# ==========================================
# 根据模式选择 YAML 后缀
# ==========================================
if [ "$MODE" = "local" ]; then
    JM_DEPLOY="flink-jobmanager-deployment-local.yaml"
    TM_DEPLOY="flink-taskmanager-deployment-local.yaml"
    BE_DEPLOY="monitor-backend-deployment-local.yaml"
    FE_DEPLOY="monitor-frontend-deployment-local.yaml"
    NEED_PVC=false
    NEED_SECRET=false
else
    JM_DEPLOY="flink-jobmanager-deployment.yaml"
    TM_DEPLOY="flink-taskmanager-deployment.yaml"
    BE_DEPLOY="monitor-backend-deployment.yaml"
    FE_DEPLOY="monitor-frontend-deployment.yaml"
    NEED_PVC=true
    NEED_SECRET=true
fi

# ==========================================
# 辅助函数
# ==========================================

# 检测 Kind 节点并加载镜像
load_image() {
    local image=$1
    local kind_node=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E "control-plane|kind" | head -1 || true)
    if [ -n "$kind_node" ]; then
        docker save "$image" | docker exec -i "$kind_node" ctr -n k8s.io images import --all-platforms - 2>/dev/null
    fi
}

wait_pod() {
    local label=$1 name=$2 timeout=${3:-300s}
    echo -n "  ${name}..."
    if kubectl wait --for=condition=ready pod -l "$label" -n flink --timeout="$timeout" &>/dev/null; then
        echo -e " ${GREEN}✓${NC}"
    else
        echo -e " ${RED}✗${NC}"
        echo -e "    ${RED}日志: kubectl logs -n flink -l ${label} --tail=20${NC}"
        kubectl logs -n flink -l "$label" --tail=10 2>/dev/null || true
    fi
}

cd "$SCRIPT_DIR"

# ==========================================
# 清理旧部署（如果 --redeploy）
# ==========================================
if [ "$REDEPLOY" = true ]; then
    echo ""
    echo -e "${BLUE}>>> 清理旧部署...${NC}"
    kubectl delete deployment --all -n flink --ignore-not-found=true 2>/dev/null || true
    kubectl delete configmap flink-cluster-cluster-config-map -n flink --ignore-not-found=true 2>/dev/null || true
    echo -e "${GREEN}✓ 已清理${NC}"
fi

# ==========================================
# 本地模式：构建并加载镜像
# ==========================================
if [ "$MODE" = "local" ]; then
    echo ""
    echo -e "${BLUE}>>> 构建 Docker 镜像...${NC}"

    # 检查 JAR
    if ! ls "$PROJECT_ROOT"/flink-jobs/target/flink-jobs-*-SNAPSHOT.jar &>/dev/null || \
       ! ls "$PROJECT_ROOT"/monitor-backend/target/monitor-backend-*-SNAPSHOT.jar &>/dev/null; then
        echo "  Maven 构建中..."
        mvn -f "$PROJECT_ROOT/pom.xml" clean package -DskipTests -q 2>&1 | tail -1
    fi

    IMAGE_TAG="local-$(date +%s)"

    echo "  flink-jobmanager:${IMAGE_TAG}"
    docker build -f "$PROJECT_ROOT/docker/jobmanager/Dockerfile" -t "flink-jobmanager:${IMAGE_TAG}" "$PROJECT_ROOT" -q
    echo "  flink-taskmanager:${IMAGE_TAG}"
    docker build -f "$PROJECT_ROOT/docker/taskmanager/Dockerfile" -t "flink-taskmanager:${IMAGE_TAG}" "$PROJECT_ROOT" -q
    echo "  monitor-backend:${IMAGE_TAG}"
    docker build -f "$PROJECT_ROOT/monitor-backend/Dockerfile" -t "monitor-backend:${IMAGE_TAG}" "$PROJECT_ROOT" -q
    echo "  monitor-frontend:${IMAGE_TAG}"
    docker build -f "$PROJECT_ROOT/monitor/frontend-vue/Dockerfile" -t "monitor-frontend:${IMAGE_TAG}" "$PROJECT_ROOT/monitor/frontend-vue/" -q

    echo -e "${GREEN}✓ 镜像构建完成${NC}"

    echo ""
    echo -e "${BLUE}>>> 加载镜像到集群...${NC}"
    load_image "flink-jobmanager:${IMAGE_TAG}"
    load_image "flink-taskmanager:${IMAGE_TAG}"
    load_image "monitor-backend:${IMAGE_TAG}"
    load_image "monitor-frontend:${IMAGE_TAG}"
    echo -e "${GREEN}✓ 镜像加载完成${NC}"
fi

# ==========================================
# 部署 K8s 资源
# ==========================================
echo ""
echo -e "${BLUE}>>> 部署 K8s 资源...${NC}"

# 1. 命名空间 + RBAC
echo -e "  ${BLUE}[1/7] 命名空间 + RBAC${NC}"
kubectl apply -f namespace.yaml
kubectl apply -f flink-rbac.yaml
echo -e "  ${GREEN}✓${NC}"

# 2. Secret（仅生产）
if [ "$NEED_SECRET" = true ]; then
    echo -e "  ${BLUE}[2/7] Secret${NC}"
    if [ -f "flink-secrets.yaml" ]; then
        kubectl apply -f flink-secrets.yaml
        echo -e "  ${GREEN}✓${NC}"
    else
        echo -e "  ${RED}✗ flink-secrets.yaml 不存在，请先创建:${NC}"
        echo "    cp flink-secrets.yaml.example flink-secrets.yaml"
        exit 1
    fi
else
    echo -e "  ${BLUE}[2/7] Secret${NC} (本地模式跳过)"
fi

# 3. PVC（仅生产）
if [ "$NEED_PVC" = true ]; then
    echo -e "  ${BLUE}[3/7] PVC${NC}"
    kubectl apply -f flink-pvc.yaml
    echo -e "  ${GREEN}✓${NC}"
else
    echo -e "  ${BLUE}[3/7] PVC${NC} (本地模式使用 hostPath)"
fi

# 4. ConfigMap
echo -e "  ${BLUE}[4/7] ConfigMap${NC}"
kubectl apply -f flink-configuration-configmap.yaml
echo -e "  ${GREEN}✓${NC}"

# 部署函数：本地模式替换镜像标签
apply_deployment() {
    local file=$1
    if [ "$MODE" = "local" ] && [ -n "$IMAGE_TAG" ]; then
        # 替换所有 image: xxx:yyy 为带时间戳的标签
        sed -E "s|image: (flink-jobmanager\|flink-taskmanager\|monitor-backend\|monitor-frontend):.*|image: \1:${IMAGE_TAG}|g" "$file" | kubectl apply -f -
    else
        kubectl apply -f "$file"
    fi
}

# 5. JobManager
echo -e "  ${BLUE}[5/7] JobManager${NC}"
kubectl apply -f flink-jobmanager-service.yaml
apply_deployment "$JM_DEPLOY"
echo -e "  ${GREEN}✓${NC}"

# 6. TaskManager
echo -e "  ${BLUE}[6/7] TaskManager${NC}"
apply_deployment "$TM_DEPLOY"
echo -e "  ${GREEN}✓${NC}"

# 7. Monitor
echo -e "  ${BLUE}[7/7] Monitor Backend + Frontend${NC}"
apply_deployment "$BE_DEPLOY"
apply_deployment "$FE_DEPLOY"
echo -e "  ${GREEN}✓${NC}"

# ==========================================
# 等待就绪
# ==========================================
echo ""
echo -e "${BLUE}>>> 等待 Pod 就绪...${NC}"
wait_pod "app=flink,component=jobmanager" "JobManager" "180s"
wait_pod "app=flink,component=taskmanager" "TaskManager" "180s"
wait_pod "app=monitor-backend" "Monitor Backend" "120s"
wait_pod "app=monitor-frontend" "Monitor Frontend" "60s"

# ==========================================
# 完成
# ==========================================
echo ""
echo "=========================================="
echo -e "${GREEN}部署完成${NC}"
echo "=========================================="
echo ""
kubectl get pods -n flink
echo ""
kubectl get svc -n flink
echo ""
echo "访问:"
echo "  Flink UI:  kubectl port-forward -n flink svc/flink-jobmanager-rest 8081:8081"
echo "  Frontend:  kubectl port-forward -n flink svc/monitor-frontend 8888:80"
echo "  Backend:   kubectl port-forward -n flink svc/monitor-backend 5001:5001"
echo "  NodePort:  http://localhost:30888"
echo ""
echo "常用命令:"
echo "  重新部署: $0 --redeploy"
echo "  卸载:     $SCRIPT_DIR/undeploy.sh"
echo ""
