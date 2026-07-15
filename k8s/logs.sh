#!/bin/bash

# Kubernetes 日志查看脚本
# 快速查看 flink 命名空间下各组件的 Pod 日志，支持跟随、崩溃前日志、批量导出、节点日志
#
# 用法:
#   ./logs.sh list                      # 列出所有 Pod 及状态
#   ./logs.sh <组件> [选项]             # 查看某组件日志
#   ./logs.sh backend                   # monitor-backend 日志（最近200行，kubectl stdout）
#   ./logs.sh backend -f                # 跟随（实时）
#   ./logs.sh backend -p                # 上次崩溃前的日志（--previous）
#   ./logs.sh backend -n 500            # 最近 500 行
#   ./logs.sh backend-file              # 容器内 /app/out/monitor-backend.log
#   ./logs.sh backend-file -f           # 跟随文件日志
#   ./logs.sh backend-file -n 500
#   ./logs.sh jobmanager -f             # Flink JobManager 实时日志
#   ./logs.sh taskmanager               # 所有 TaskManager 聚合日志
#   ./logs.sh pod <pod名> [选项]        # 按 Pod 名精确查看
#   ./logs.sh dump [目录]               # 导出所有 Pod 日志到目录（默认 ./pod-logs）
#   ./logs.sh node                      # 查看节点(kubelet/容器运行时)日志
#
# 组件别名: jobmanager(jm) | taskmanager(tm) | backend(be) | frontend(fe)

set -e

NAMESPACE="flink"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ==========================================
# 前置检查
# ==========================================
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}错误: kubectl 未安装${NC}"; exit 1
fi
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"; exit 1
fi

# 组件别名 → label selector
resolve_selector() {
    case "$1" in
        jobmanager|jm)   echo "app=flink,component=jobmanager" ;;
        taskmanager|tm)  echo "app=flink,component=taskmanager" ;;
        backend|be)      echo "app=monitor-backend" ;;
        frontend|fe)     echo "app=monitor-frontend" ;;
        *)               echo "" ;;
    esac
}

# 解析可选参数：-f 跟随, -p 崩溃前, -n 行数
parse_log_opts() {
    LOG_ARGS=()
    TAIL="200"
    shift  # 去掉组件名
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -f|--follow)    LOG_ARGS+=("-f"); shift ;;
            -p|--previous)  LOG_ARGS+=("--previous"); shift ;;
            -n|--tail)      TAIL="$2"; shift 2 ;;
            *)              shift ;;
        esac
    done
    LOG_ARGS+=("--tail=${TAIL}")
}

# ==========================================
# 子命令
# ==========================================
list_pods() {
    echo -e "${BLUE}>>> ${NAMESPACE} 命名空间 Pod${NC}"
    kubectl get pods -n "$NAMESPACE" -o wide 2>/dev/null || echo "  (无 Pod)"
}

logs_by_component() {
    local comp=$1
    local selector
    selector=$(resolve_selector "$comp")
    if [ -z "$selector" ]; then
        echo -e "${RED}未知组件: ${comp}${NC}"
        echo "可用组件: jobmanager(jm) | taskmanager(tm) | backend(be) | frontend(fe)"
        exit 1
    fi

    # 校验是否有匹配 Pod
    local count
    count=$(kubectl get pods -n "$NAMESPACE" -l "$selector" --no-headers 2>/dev/null | wc -l | tr -d ' ')
    if [ "$count" = "0" ]; then
        echo -e "${YELLOW}未找到组件 ${comp} 的 Pod（selector: ${selector}）${NC}"
        list_pods
        exit 1
    fi

    parse_log_opts "$@"
    echo -e "${BLUE}>>> ${comp} 日志 (${count} 个 Pod, selector: ${selector})${NC}"
    # --prefix 标注来源 Pod，--max-log-requests 支持多 Pod 聚合
    kubectl logs -n "$NAMESPACE" -l "$selector" \
        --all-containers=true --prefix=true --max-log-requests=20 \
        "${LOG_ARGS[@]}"
}

logs_by_pod() {
    local pod=$1
    if [ -z "$pod" ]; then
        echo -e "${RED}请提供 Pod 名${NC}"; list_pods; exit 1
    fi
    parse_log_opts "$@"
    echo -e "${BLUE}>>> Pod ${pod} 日志${NC}"
    kubectl logs -n "$NAMESPACE" "$pod" --all-containers=true "${LOG_ARGS[@]}"
}

dump_logs() {
    local outdir="${1:-./pod-logs}"
    mkdir -p "$outdir"
    echo -e "${BLUE}>>> 导出所有 Pod 日志到 ${outdir}/${NC}"
    local pods
    pods=$(kubectl get pods -n "$NAMESPACE" -o jsonpath='{.items[*].metadata.name}' 2>/dev/null)
    if [ -z "$pods" ]; then
        echo -e "${YELLOW}无 Pod 可导出${NC}"; exit 0
    fi
    for pod in $pods; do
        local f="${outdir}/${pod}.log"
        kubectl logs -n "$NAMESPACE" "$pod" --all-containers=true --tail=-1 > "$f" 2>&1 || true
        echo -e "  ${GREEN}✓${NC} ${f}"
        # 若有崩溃历史，一并导出 previous
        if kubectl logs -n "$NAMESPACE" "$pod" --previous --tail=1 &>/dev/null; then
            kubectl logs -n "$NAMESPACE" "$pod" --all-containers=true --previous > "${outdir}/${pod}.previous.log" 2>&1 || true
            echo -e "  ${GREEN}✓${NC} ${outdir}/${pod}.previous.log (崩溃前)"
        fi
        if [[ "$pod" == *monitor-backend* ]]; then
            kubectl exec -n "$NAMESPACE" "$pod" -- sh -c \
                'test -f /app/out/monitor-backend.log && cat /app/out/monitor-backend.log' \
                > "${outdir}/${pod}.file.log" 2>/dev/null \
                && echo -e "  ${GREEN}✓${NC} ${outdir}/${pod}.file.log (/app/out)" || true
        fi
    done
    echo -e "${GREEN}导出完成${NC}"
}

# 读取容器内 /app/out/monitor-backend.log
logs_backend_file() {
    local follow=false
    local lines=200
    shift  # 去掉 backend-file
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -f|--follow) follow=true; shift ;;
            -n|--tail)   lines="$2"; shift 2 ;;
            *) shift ;;
        esac
    done

    local pod
    pod=$(kubectl get pods -n "$NAMESPACE" -l app=monitor-backend \
        --field-selector=status.phase=Running \
        -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -z "$pod" ]; then
        echo -e "${YELLOW}未找到 Running 的 monitor-backend Pod${NC}"
        list_pods
        exit 1
    fi

    local logfile="/app/out/monitor-backend.log"
    echo -e "${BLUE}>>> ${pod}:${logfile}${NC}"
    if [ "$follow" = true ]; then
        kubectl exec -n "$NAMESPACE" "$pod" -- sh -c "tail -n ${lines} -F ${logfile}"
    else
        kubectl exec -n "$NAMESPACE" "$pod" -- sh -c \
            "tail -n ${lines} ${logfile} 2>/dev/null || echo '(文件尚不存在，请确认已重建 backend 镜像并滚动重启)'"
    fi
}

# 节点日志：KIND/Docker Desktop 节点是容器，通过 docker exec 读 kubelet/containerd 日志
node_logs() {
    echo -e "${BLUE}>>> 集群节点${NC}"
    kubectl get nodes -o wide 2>/dev/null
    echo ""
    local node
    node=$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | grep 'kindest/node' | awk '{print $1}' | head -1 || true)
    if [ -z "$node" ]; then
        echo -e "${YELLOW}未检测到 KIND/Docker Desktop 节点容器，无法读取节点系统日志${NC}"
        echo "远程集群请登录节点主机后执行: journalctl -u kubelet -f"
        exit 0
    fi
    echo -e "${BLUE}>>> 节点 ${node} kubelet 日志 (最近100行)${NC}"
    docker exec "$node" journalctl -u kubelet --no-pager -n 100 2>/dev/null \
        || docker exec "$node" sh -c 'crictl logs $(crictl ps -q | head -1) 2>/dev/null' \
        || echo -e "${YELLOW}无法读取 kubelet 日志${NC}"
}

# ==========================================
# 主入口
# ==========================================
case "${1:-}" in
    ""|-h|--help)
        echo "用法: $0 <命令|组件> [选项]"
        echo ""
        echo "命令:"
        echo "  list                列出所有 Pod"
        echo "  pod <名称> [选项]   按 Pod 名查看日志"
        echo "  dump [目录]         导出所有 Pod 日志（默认 ./pod-logs）"
        echo "  node                查看节点 kubelet 日志"
        echo "  backend-file [选项] 容器文件日志 /app/out/monitor-backend.log"
        echo ""
        echo "组件 (可直接作为命令):"
        echo "  jobmanager(jm) | taskmanager(tm) | backend(be) | frontend(fe)"
        echo ""
        echo "选项:"
        echo "  -f          跟随实时日志"
        echo "  -p          查看上次崩溃前日志"
        echo "  -n <行数>   显示最近 N 行（默认 200）"
        echo ""
        echo "示例:"
        echo "  $0 backend -f          # 实时跟随后端 stdout"
        echo "  $0 backend-file -f     # 跟随 /app/out 文件日志"
        echo "  $0 jobmanager -n 500   # JobManager 最近 500 行"
        echo "  $0 taskmanager -p      # TaskManager 崩溃前日志"
        exit 0 ;;
    list)  list_pods ;;
    pod)   shift; logs_by_pod "$@" ;;
    dump)  dump_logs "$2" ;;
    node)  node_logs ;;
    backend-file|be-file) logs_backend_file "$@" ;;
    *)     logs_by_component "$@" ;;
esac
