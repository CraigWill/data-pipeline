#!/bin/bash

# Kubernetes 进入容器脚本
# 进入 flink 命名空间下各组件的 Pod（kubectl exec -it）
#
# 用法:
#   ./shell.sh                          # 交互选择组件
#   ./shell.sh list                     # 列出所有 Pod
#   ./shell.sh backend                  # 进入 monitor-backend
#   ./shell.sh jobmanager               # 进入 Flink JobManager
#   ./shell.sh taskmanager              # 进入 TaskManager（多副本时可选）
#   ./shell.sh frontend                 # 进入 monitor-frontend
#   ./shell.sh obbinlog                 # 进入 obbinlog（若已部署）
#   ./shell.sh pod <pod名>              # 按 Pod 名进入
#   ./shell.sh backend -- bash          # 指定命令（默认 sh）
#   ./shell.sh backend -- env | grep OSS  # 在容器内执行命令后退出
#
# 组件别名: jobmanager(jm) | taskmanager(tm) | backend(be) | frontend(fe) | obbinlog(ob)

set -e

NAMESPACE="${NAMESPACE:-flink}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}错误: kubectl 未安装${NC}"; exit 1
fi
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"; exit 1
fi

resolve_selector() {
    case "$1" in
        jobmanager|jm)   echo "app=flink,component=jobmanager" ;;
        taskmanager|tm)  echo "app=flink,component=taskmanager" ;;
        backend|be)      echo "app=monitor-backend" ;;
        frontend|fe)     echo "app=monitor-frontend" ;;
        obbinlog|ob)     echo "app=obbinlog" ;;
        *)               echo "" ;;
    esac
}

list_pods() {
    echo -e "${BLUE}>>> ${NAMESPACE} 命名空间 Pod${NC}"
    kubectl get pods -n "$NAMESPACE" -o wide 2>/dev/null || echo "  (无 Pod)"
}

# 按 selector 取 Running Pod 列表
get_running_pods() {
    local selector=$1
    kubectl get pods -n "$NAMESPACE" -l "$selector" \
        --field-selector=status.phase=Running \
        -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null
}

# 多副本时让用户选一个
pick_pod() {
    local pods=("$@")
    if [ ${#pods[@]} -eq 0 ]; then
        return 1
    fi
    if [ ${#pods[@]} -eq 1 ]; then
        echo "${pods[0]}"
        return 0
    fi
    echo -e "${YELLOW}发现多个 Pod，请选择:${NC}" >&2
    local i=1
    for p in "${pods[@]}"; do
        echo "  $i) $p" >&2
        i=$((i + 1))
    done
    echo -n "输入编号 [1]: " >&2
    read -r choice
    choice=${choice:-1}
    if ! [[ "$choice" =~ ^[0-9]+$ ]] || [ "$choice" -lt 1 ] || [ "$choice" -gt ${#pods[@]} ]; then
        echo -e "${RED}无效选择${NC}" >&2
        return 1
    fi
    echo "${pods[$((choice - 1))]}"
}

# 容器内优先 bash，否则 sh
default_shell_cmd() {
    echo 'if command -v bash >/dev/null 2>&1; then exec bash; else exec sh; fi'
}

exec_pod() {
    local pod=$1
    shift
    local cmd=("$@")

    if [ ${#cmd[@]} -eq 0 ]; then
        echo -e "${GREEN}>>> 进入 Pod: ${pod} (${NAMESPACE})${NC}"
        # 先试 bash，失败再 sh（部分镜像无 bash）
        if kubectl exec -it -n "$NAMESPACE" "$pod" -- bash 2>/dev/null; then
            return 0
        fi
        kubectl exec -it -n "$NAMESPACE" "$pod" -- sh
        return
    fi

    echo -e "${GREEN}>>> 在 Pod ${pod} 执行: ${cmd[*]}${NC}"
    kubectl exec -it -n "$NAMESPACE" "$pod" -- "${cmd[@]}"
}

enter_component() {
    local comp=$1
    shift
    local selector
    selector=$(resolve_selector "$comp")
    if [ -z "$selector" ]; then
        echo -e "${RED}未知组件: ${comp}${NC}"
        usage
        exit 1
    fi

    local pods=()
    while IFS= read -r line; do
        [ -n "$line" ] && pods+=("$line")
    done < <(get_running_pods "$selector")

    if [ ${#pods[@]} -eq 0 ]; then
        echo -e "${RED}没有 Running 的 Pod（selector=${selector}）${NC}"
        list_pods
        exit 1
    fi

    local pod
    pod=$(pick_pod "${pods[@]}") || exit 1
    exec_pod "$pod" "$@"
}

enter_by_pod_name() {
    local name=$1
    shift
    if ! kubectl get pod -n "$NAMESPACE" "$name" &>/dev/null; then
        echo -e "${RED}Pod 不存在: ${name}${NC}"
        list_pods
        exit 1
    fi
    exec_pod "$name" "$@"
}

interactive_menu() {
    echo -e "${BLUE}>>> 选择要进入的组件 (${NAMESPACE})${NC}"
    echo "  1) jobmanager"
    echo "  2) taskmanager"
    echo "  3) backend"
    echo "  4) frontend"
    echo "  5) obbinlog"
    echo "  6) list pods"
    echo -n "输入编号: "
    read -r choice
    case "$choice" in
        1) enter_component jobmanager ;;
        2) enter_component taskmanager ;;
        3) enter_component backend ;;
        4) enter_component frontend ;;
        5) enter_component obbinlog ;;
        6) list_pods ;;
        *) echo -e "${RED}无效选择${NC}"; exit 1 ;;
    esac
}

usage() {
    cat <<'EOF'
用法:
  ./shell.sh                          # 交互选择
  ./shell.sh list                     # 列出 Pod
  ./shell.sh <组件>                   # 进入容器（bash/sh）
  ./shell.sh <组件> -- <命令...>      # 在容器内执行命令
  ./shell.sh pod <pod名> [-- 命令...] # 按 Pod 名进入

组件别名: jobmanager(jm) | taskmanager(tm) | backend(be) | frontend(fe) | obbinlog(ob)

示例:
  ./shell.sh backend
  ./shell.sh tm
  ./shell.sh backend -- env | grep OSS
  ./shell.sh pod flink-jobmanager-xxxxx -- ls /opt/flink/conf
EOF
}

# 解析：组件 [-- 命令...]
CMD=()
COMPONENT=""
case "${1:-}" in
    ""|-h|--help|help)
        if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "help" ]; then
            usage; exit 0
        fi
        interactive_menu
        exit 0
        ;;
    list|ls|pods)
        list_pods
        exit 0
        ;;
    pod)
        if [ -z "${2:-}" ]; then
            echo -e "${RED}请指定 Pod 名: ./shell.sh pod <pod名>${NC}"
            exit 1
        fi
        POD_NAME="$2"
        shift 2
        if [ "${1:-}" = "--" ]; then shift; fi
        enter_by_pod_name "$POD_NAME" "$@"
        exit 0
        ;;
    *)
        COMPONENT="$1"
        shift
        if [ "${1:-}" = "--" ]; then shift; fi
        enter_component "$COMPONENT" "$@"
        exit 0
        ;;
esac
