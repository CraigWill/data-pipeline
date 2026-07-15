#!/bin/bash

# Kubernetes 本地端口转发脚本
# KIND / Docker Desktop 下 NodePort 常不通，用本脚本把服务映射到本机端口。
#
# 用法:
#   ./port-forward.sh              # 等同 start
#   ./port-forward.sh start        # 启动（先停旧进程再开）
#   ./port-forward.sh stop         # 停止全部映射
#   ./port-forward.sh restart      # 重启
#   ./port-forward.sh status       # 查看监听与健康检查
#
# 映射:
#   前端     http://localhost:8888  → svc/monitor-frontend:80
#   后端 API http://localhost:5001  → svc/monitor-backend:5001
#   Flink UI http://localhost:8081  → svc/flink-jobmanager-rest:8081

set -e

NAMESPACE="flink"
ADDRESS="0.0.0.0"
LOG_DIR="${PORT_FORWARD_LOG_DIR:-/tmp}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# name|local_port|svc|remote_port
FORWARDS=(
  "frontend|8888|monitor-frontend|80"
  "backend|5001|monitor-backend|5001"
  "flink|8081|flink-jobmanager-rest|8081"
)

check_prereq() {
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}错误: kubectl 未安装${NC}"; exit 1
    fi
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"; exit 1
    fi
}

port_listening() {
    local port=$1
    if command -v lsof &>/dev/null; then
        lsof -iTCP:"${port}" -sTCP:LISTEN -t &>/dev/null
    else
        # 无 lsof 时用 nc 探测
        nc -z 127.0.0.1 "${port}" &>/dev/null
    fi
}

stop_forwards() {
    echo -e "${BLUE}>>> 停止端口映射...${NC}"
    # 覆盖: kubectl -n flink port-forward / kubectl port-forward -n flink / 旧写法
    if pkill -f "kubectl port-forward.*${NAMESPACE}" 2>/dev/null \
        || pkill -f "kubectl.*port-forward.*(monitor-frontend|monitor-backend|flink-jobmanager-rest)" 2>/dev/null; then
        sleep 1
        echo -e "${GREEN}✓ 已停止${NC}"
    else
        echo "  (无运行中的 port-forward)"
    fi
}

start_forwards() {
    check_prereq
    stop_forwards
    echo -e "${BLUE}>>> 启动端口映射 (namespace=${NAMESPACE})...${NC}"

    local entry name local_port svc remote_port
    for entry in "${FORWARDS[@]}"; do
        IFS='|' read -r name local_port svc remote_port <<< "$entry"
        local log_file="${LOG_DIR}/pf-${name}.log"
        kubectl port-forward -n "$NAMESPACE" --address "$ADDRESS" \
            "svc/${svc}" "${local_port}:${remote_port}" \
            > "$log_file" 2>&1 &
        echo "  ${name}: localhost:${local_port} → ${svc}:${remote_port}  (log: ${log_file})"
    done

    sleep 2
    verify_and_print
}

verify_and_print() {
    local ok=true
    local entry name local_port svc remote_port
    for entry in "${FORWARDS[@]}"; do
        IFS='|' read -r name local_port svc remote_port <<< "$entry"
        if port_listening "$local_port"; then
            echo -e "  ${GREEN}✓${NC} ${name} :${local_port} 监听中"
        else
            echo -e "  ${YELLOW}⚠${NC} ${name} :${local_port} 未监听，请检查 ${LOG_DIR}/pf-${name}.log"
            ok=false
        fi
    done

    echo ""
    if [ "$ok" = true ]; then
        echo -e "${GREEN}✓ 端口映射已就绪${NC}"
        echo ""
        echo "  前端:     http://localhost:8888"
        echo "  后端 API: http://localhost:5001"
        echo "  Flink UI: http://localhost:8081"
    else
        echo -e "${YELLOW}部分端口未就绪。可试 NodePort: Frontend=30888 Backend=30501 Flink=30081${NC}"
        return 1
    fi
}

status_forwards() {
    echo -e "${BLUE}>>> 端口映射状态${NC}"
    local entry name local_port svc remote_port
    for entry in "${FORWARDS[@]}"; do
        IFS='|' read -r name local_port svc remote_port <<< "$entry"
        if port_listening "$local_port"; then
            echo -e "  ${GREEN}✓${NC} ${name}  http://localhost:${local_port}  (→ svc/${svc}:${remote_port})"
        else
            echo -e "  ${RED}✗${NC} ${name}  :${local_port} 未监听"
        fi
    done

    echo ""
    if command -v curl &>/dev/null && port_listening 5001; then
        local code
        code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 2 http://127.0.0.1:5001/actuator/health || echo "000")
        if [ "$code" = "200" ]; then
            echo -e "  ${GREEN}✓${NC} backend health: ${code}"
        else
            echo -e "  ${YELLOW}⚠${NC} backend health: ${code}（端口在听但服务可能未就绪）"
        fi
    fi

    echo ""
    echo "进程:"
    local found=false
    while IFS= read -r line; do
        echo "  $line"
        found=true
    done < <(pgrep -fl "port-forward" 2>/dev/null | grep -E "kubectl|${NAMESPACE}" || true)
    if [ "$found" = false ]; then
        echo "  (无 kubectl port-forward 进程；若端口仍在听，可能是其他会话/工具占用)"
    fi
}

usage() {
    cat <<EOF
用法: $0 [start|stop|restart|status]

  start    启动端口映射（默认）
  stop     停止全部映射
  restart  重启
  status   查看状态与后端健康检查

映射:
  前端     http://localhost:8888
  后端 API http://localhost:5001
  Flink UI http://localhost:8081
EOF
}

CMD="${1:-start}"
case "$CMD" in
    start)   start_forwards ;;
    stop)    stop_forwards ;;
    restart) start_forwards ;;
    status)  status_forwards ;;
    -h|--help|help) usage ;;
    *)
        echo -e "${RED}未知命令: $CMD${NC}"
        usage
        exit 1
        ;;
esac
