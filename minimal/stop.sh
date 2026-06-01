#!/bin/bash
# 最小化部署停止脚本

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$SCRIPT_DIR/.pids"

# 加载环境变量获取 FLINK_HOME
if [ -f "$SCRIPT_DIR/env.sh" ]; then
    source "$SCRIPT_DIR/env.sh"
fi

echo -e "${BLUE}>>> 停止所有服务${NC}"

# ---- 停止 Backend ----
stopped=false

# 方法1: 通过 PID 文件
if [ -f "$PID_DIR/backend.pid" ]; then
    pid=$(cat "$PID_DIR/backend.pid")
    if kill -0 "$pid" 2>/dev/null; then
        kill "$pid"
        echo -e "${GREEN}✓ Backend 已停止 (PID: $pid)${NC}"
        stopped=true
    fi
    rm -f "$PID_DIR/backend.pid"
fi

# 方法2: 通过进程名（PID 文件不存在时的兜底）
if [ "$stopped" = false ]; then
    pids=$(pgrep -f "monitor-backend.*SNAPSHOT.jar" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo "$pids" | xargs kill 2>/dev/null || true
        echo -e "${GREEN}✓ Backend 已停止 (PID: $pids)${NC}"
        stopped=true
    fi
fi

if [ "$stopped" = false ]; then
    echo -e "${YELLOW}  Backend 未运行${NC}"
fi

# ---- 停止 Frontend ----
stopped=false

if [ -f "$PID_DIR/frontend.pid" ]; then
    content=$(cat "$PID_DIR/frontend.pid")
    if [ "$content" = "nginx" ]; then
        # nginx 需要 sudo 权限停止
        if sudo nginx -s stop 2>/dev/null; then
            echo -e "${GREEN}✓ Frontend (nginx) 已停止${NC}"
        elif sudo pkill -f "nginx.*:8888" 2>/dev/null; then
            echo -e "${GREEN}✓ Frontend (nginx) 已停止 (通过 pkill)${NC}"
        fi
        stopped=true
    elif kill -0 "$content" 2>/dev/null; then
        kill "$content"
        echo -e "${GREEN}✓ Frontend 已停止 (PID: $content)${NC}"
        stopped=true
    fi
    rm -f "$PID_DIR/frontend.pid"
fi

# 兜底：通过进程名
if [ "$stopped" = false ]; then
    # 先尝试 nginx（需要 sudo）
    if pgrep -f "nginx.*:8888" >/dev/null 2>&1; then
        sudo pkill -f "nginx.*:8888" 2>/dev/null && echo -e "${GREEN}✓ Frontend (nginx) 已停止${NC}" || true
        stopped=true
    fi
    # 再尝试 Vite/Python
    pids=$(pgrep -f "http.server 8888\|vite.*3000" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo "$pids" | xargs kill 2>/dev/null || true
        echo -e "${GREEN}✓ Frontend 已停止${NC}"
    fi
fi

# ---- 停止 Flink ----
FLINK_HOME="${FLINK_HOME:-/usr/local/flink-1.20.0}"
flink_stopped=false

# 方法1: 通过 stop-cluster.sh
if [ -f "$FLINK_HOME/bin/stop-cluster.sh" ]; then
    "$FLINK_HOME/bin/stop-cluster.sh" 2>/dev/null && flink_stopped=true || true
fi

# 方法2: 通过进程名（stop-cluster.sh 找不到 PID 文件时的兜底）
flink_pids=$(pgrep -f "StandaloneSessionClusterEntrypoint\|TaskManagerRunner" 2>/dev/null || true)
if [ -n "$flink_pids" ]; then
    echo "$flink_pids" | xargs kill 2>/dev/null || true
    echo -e "${GREEN}✓ Flink 进程已停止 (PID: $flink_pids)${NC}"
    flink_stopped=true
fi

if [ "$flink_stopped" = true ]; then
    echo -e "${GREEN}✓ Flink 集群已停止${NC}"
else
    echo -e "${YELLOW}  Flink 未运行${NC}"
fi

echo -e "${GREEN}✓ 完成${NC}"
