#!/usr/bin/env bash
# 停止 localosstest 启动的本地 Flink 集群
set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNTIME_DIR="$SCRIPT_DIR/runtime"
CONF_DIR="$RUNTIME_DIR/conf"
LOG_DIR="$RUNTIME_DIR/logs"
PID_DIR="$RUNTIME_DIR/pids"

if [ -f "$SCRIPT_DIR/env.sh" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/env.sh"
fi

FLINK_HOME="${FLINK_HOME:-/usr/local/flink-1.20.0}"
REST_PORT="${REST_PORT:-18081}"

export FLINK_CONF_DIR="$CONF_DIR"
export FLINK_LOG_DIR="$LOG_DIR"
export FLINK_PID_DIR="$PID_DIR"

echo -e "${BLUE}>>> 停止 localosstest Flink${NC}"

if [ -x "$FLINK_HOME/bin/stop-cluster.sh" ]; then
    "$FLINK_HOME/bin/stop-cluster.sh" 2>/dev/null || true
fi

# 兜底：按本目录 pid / 进程名清理（避免误杀 Kind 容器内进程——容器不在本机进程树）
if [ -d "$PID_DIR" ]; then
    for f in "$PID_DIR"/*.pid; do
        [ -f "$f" ] || continue
        pid="$(cat "$f" 2>/dev/null || true)"
        if [ -n "${pid:-}" ] && kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null || true
            echo -e "${GREEN}✓ 已停 PID $pid ($f)${NC}"
        fi
        rm -f "$f"
    done
fi

# 仅匹配本机 Standalone（带本 REST 端口的 JM 更安全，但进程参数里不一定有端口）
# 若全局还有其它裸机 Flink，请先确认再手动 kill
if curl -sf "http://localhost:${REST_PORT}/overview" >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠ REST ${REST_PORT} 仍可达，尝试按进程名停止${NC}"
    pgrep -f "StandaloneSessionClusterEntrypoint|TaskManagerRunner" 2>/dev/null | xargs kill 2>/dev/null || true
    sleep 1
fi

if curl -sf "http://localhost:${REST_PORT}/overview" >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠ 仍在运行: http://localhost:${REST_PORT}${NC}"
else
    echo -e "${GREEN}✓ 已停止（或未在运行）${NC}"
fi
