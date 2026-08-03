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

# shellcheck disable=SC1091
source "$SCRIPT_DIR/_lib.sh"

if [ -f "$SCRIPT_DIR/env.sh" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/env.sh"
fi

FLINK_HOME="${FLINK_HOME:-/usr/local/flink-1.20.0}"
normalize_flink_home 2>/dev/null || true
REST_PORT="${REST_PORT:-18081}"

export FLINK_CONF_DIR="$(to_flink_path "$CONF_DIR")"
export FLINK_LOG_DIR="$(to_flink_path "$LOG_DIR")"
export FLINK_PID_DIR="$(to_flink_path "$PID_DIR")"

echo -e "${BLUE}>>> 停止 localosstest Flink${NC}"

stopper="$(stop_cluster_script)"
if [ -f "$stopper" ]; then
    run_flink_script "$stopper" 2>/dev/null || true
fi

# 兜底：按本目录 pid 清理
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

# Unix 兜底按进程名；Windows 上 pgrep 可能不存在
if curl -sf "http://localhost:${REST_PORT}/overview" >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠ REST ${REST_PORT} 仍可达，尝试按进程名停止${NC}"
    if command -v pgrep >/dev/null 2>&1; then
        pgrep -f "StandaloneSessionClusterEntrypoint|TaskManagerRunner" 2>/dev/null | xargs kill 2>/dev/null || true
    elif is_windows; then
        echo -e "${YELLOW}  Windows 上请再执行一次 stop-cluster.bat，或在任务管理器结束 StandaloneSession / TaskManagerRunner${NC}"
    fi
    sleep 1
fi

if curl -sf "http://localhost:${REST_PORT}/overview" >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠ 仍在运行: http://localhost:${REST_PORT}${NC}"
else
    echo -e "${GREEN}✓ 已停止（或未在运行）${NC}"
fi
