#!/usr/bin/env bash
# 本地访问 KIND/Docker Desktop 集群服务的端口映射
# NodePort（30888/30501/30081）在本机经常不通，优先用本脚本。
#
# 用法: ./port-forward.sh [start|stop|status|restart]
set -euo pipefail

NS="${NAMESPACE:-flink}"
PF_PATTERN='kubectl port-forward.*flink'

start() {
  stop_quiet
  sleep 0.5
  # 请在本机终端执行本脚本；Agent 内后台进程可能随会话结束
  nohup kubectl -n "$NS" port-forward --address 0.0.0.0 svc/monitor-frontend      8888:80   > /tmp/pf-frontend.log 2>&1 &
  nohup kubectl -n "$NS" port-forward --address 0.0.0.0 svc/monitor-backend       5001:5001 > /tmp/pf-backend.log  2>&1 &
  nohup kubectl -n "$NS" port-forward --address 0.0.0.0 svc/flink-jobmanager-rest 8081:8081 > /tmp/pf-flink.log    2>&1 &
  sleep 2
  status || true
}

stop_quiet() {
  pkill -f "$PF_PATTERN" 2>/dev/null || true
}

stop() {
  stop_quiet
  echo "已停止 port-forward"
}

status() {
  echo ">>> 端口映射状态"
  local ok=true
  for pair in "frontend:8888" "backend:5001" "flink:8081"; do
    name="${pair%%:*}"
    port="${pair##*:}"
    if lsof -iTCP:"$port" -sTCP:LISTEN -t &>/dev/null; then
      echo "  ✓ $name  http://localhost:$port"
    else
      echo "  ✗ $name  :$port 未监听（见 /tmp/pf-*.log）"
      ok=false
    fi
  done
  echo ""
  if curl -sf --connect-timeout 2 http://127.0.0.1:5001/actuator/health >/dev/null 2>&1 \
     || curl -sf --connect-timeout 2 -o /dev/null -w '' http://127.0.0.1:5001/ >/dev/null 2>&1; then
    echo "  ✓ backend health: OK"
  else
    echo "  ✗ backend health: 不可达"
    ok=false
  fi
  echo ""
  pgrep -fl "$PF_PATTERN" 2>/dev/null || echo "  （无 port-forward 进程）"
  $ok
}

case "${1:-start}" in
  start)   start ;;
  stop)    stop ;;
  status)  status || true ;;
  restart) start ;;
  *)
    echo "用法: $0 [start|stop|status|restart]"
    exit 1
    ;;
esac
