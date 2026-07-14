#!/bin/bash

# Pod 自动扩缩容 (HPA) 管理脚本
# 负责安装 metrics-server（HPA 的前置依赖）并管理各组件的 HorizontalPodAutoscaler
#
# 用法:
#   ./autoscale.sh install    # 安装/修复 metrics-server（KIND/Docker Desktop 需要）
#   ./autoscale.sh apply      # 部署所有 HPA（自动确保 metrics-server 就绪）
#   ./autoscale.sh status     # 查看 HPA 状态与实时指标
#   ./autoscale.sh watch      # 持续监控 HPA（Ctrl+C 退出）
#   ./autoscale.sh delete     # 删除所有 HPA
#
# 依赖: kubectl，已连接到目标集群

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE="flink"
HPA_FILE="$SCRIPT_DIR/flink-hpa.yaml"
METRICS_SERVER_URL="https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ==========================================
# 前置检查
# ==========================================
check_prereq() {
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}错误: kubectl 未安装${NC}"; exit 1
    fi
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"; exit 1
    fi
}

# 判断是否为 KIND / Docker Desktop 等本地集群（需要 --kubelet-insecure-tls）
is_local_cluster() {
    local ctx node
    ctx=$(kubectl config current-context 2>/dev/null || true)
    node=$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | grep -c 'kindest/node' || true)
    if [[ "$ctx" == *"docker-desktop"* ]] || [[ "$ctx" == *"kind"* ]] || [[ "$ctx" == *"minikube"* ]] || [ "$node" -gt 0 ]; then
        return 0
    fi
    return 1
}

# ==========================================
# 安装 metrics-server
# ==========================================
install_metrics_server() {
    echo -e "${BLUE}>>> 检查 metrics-server...${NC}"
    if kubectl get deployment metrics-server -n kube-system &>/dev/null; then
        echo -e "${GREEN}✓ metrics-server 已存在${NC}"
    else
        echo -e "${YELLOW}metrics-server 未安装，正在部署...${NC}"
        kubectl apply -f "$METRICS_SERVER_URL"
        echo -e "${GREEN}✓ 已提交 metrics-server${NC}"
    fi

    # 本地集群使用自签证书，kubelet 连接会失败，需要 --kubelet-insecure-tls
    if is_local_cluster; then
        echo -e "${YELLOW}检测到本地集群，为 metrics-server 添加 --kubelet-insecure-tls${NC}"
        kubectl patch deployment metrics-server -n kube-system --type=json \
            -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' \
            2>/dev/null || echo -e "  ${YELLOW}(参数可能已存在，跳过)${NC}"
    fi

    echo -n "  等待 metrics-server 就绪..."
    if kubectl rollout status deployment/metrics-server -n kube-system --timeout=120s &>/dev/null; then
        echo -e " ${GREEN}✓${NC}"
    else
        echo -e " ${RED}✗ 超时，请检查: kubectl -n kube-system logs deploy/metrics-server${NC}"
        return 1
    fi

    # 等待 Metrics API 真正可用
    echo -n "  等待 Metrics API 可用..."
    for i in $(seq 1 30); do
        if kubectl top nodes &>/dev/null; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        sleep 5
    done
    echo -e " ${YELLOW}⚠ Metrics API 暂未就绪，稍后可再次运行 status${NC}"
}

# ==========================================
# 应用 HPA
# ==========================================
apply_hpa() {
    if [ ! -f "$HPA_FILE" ]; then
        echo -e "${RED}错误: 未找到 $HPA_FILE${NC}"; exit 1
    fi
    install_metrics_server
    echo ""
    echo -e "${BLUE}>>> 应用 HPA 配置...${NC}"
    kubectl apply -f "$HPA_FILE"
    echo -e "${GREEN}✓ HPA 已应用${NC}"
    echo ""
    show_status
}

# ==========================================
# 状态查看
# ==========================================
show_status() {
    echo -e "${BLUE}>>> HPA 状态${NC}"
    kubectl get hpa -n "$NAMESPACE" 2>/dev/null || echo "  (无 HPA)"
    echo ""
    echo -e "${BLUE}>>> Pod 资源使用${NC}"
    kubectl top pods -n "$NAMESPACE" 2>/dev/null || echo -e "  ${YELLOW}Metrics API 未就绪，先运行: $0 install${NC}"
}

watch_hpa() {
    echo -e "${BLUE}>>> 持续监控 HPA (Ctrl+C 退出)...${NC}"
    kubectl get hpa -n "$NAMESPACE" -w
}

# ==========================================
# 删除 HPA
# ==========================================
delete_hpa() {
    echo -e "${BLUE}>>> 删除 HPA...${NC}"
    kubectl delete -f "$HPA_FILE" --ignore-not-found=true
    echo -e "${GREEN}✓ HPA 已删除${NC}"
}

# ==========================================
# 主入口
# ==========================================
check_prereq

case "${1:-}" in
    install) install_metrics_server ;;
    apply)   apply_hpa ;;
    status)  show_status ;;
    watch)   watch_hpa ;;
    delete)  delete_hpa ;;
    *)
        echo "用法: $0 {install|apply|status|watch|delete}"
        echo "  install  安装/修复 metrics-server"
        echo "  apply    部署所有 HPA（自动确保 metrics-server 就绪）"
        echo "  status   查看 HPA 状态与实时指标"
        echo "  watch    持续监控 HPA"
        echo "  delete   删除所有 HPA"
        exit 1 ;;
esac
