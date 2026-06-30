#!/bin/bash
# ============================================================
# obbinlog (OceanBase Binlog Service / LogProxy) 管理脚本
# 用法:
#   ./obbinlog.sh start    启动 obbinlog 容器（CDC 模式，连接外部 OceanBase）
#   ./obbinlog.sh stop     停止并删除 obbinlog 容器
#   ./obbinlog.sh restart  重启
#   ./obbinlog.sh status   查看状态
#   ./obbinlog.sh logs     查看日志（实时）
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── 可配置参数（可被环境变量覆盖）────────────────────────────
CONTAINER_NAME="${OBBINLOG_CONTAINER:-obbinlog}"
IMAGE="${OBBINLOG_IMAGE:-oceanbase/obbinlog-ce:4.2.5-test}"
NETWORK="${OBBINLOG_NETWORK:-flink-network}"
PORT="${OBBINLOG_PORT:-2983}"
OB_HOST_ENTRY="${OBBINLOG_OB_HOST_ENTRY:-centos-ob:172.22.0.1}"  # extra_hosts 映射
OB_SYS_USERNAME="${OB_SYS_USERNAME:-root}"
OB_SYS_PASSWORD="${OB_SYS_PASSWORD:-password}"
ENTRYPOINT_SCRIPT="${SCRIPT_DIR}/docker/obbinlog/cdc-entrypoint.sh"

# ── 颜色输出 ────────────────────────────────────────────────
info()  { echo -e "\033[0;32m[INFO]\033[0m  $*"; }
warn()  { echo -e "\033[0;33m[WARN]\033[0m  $*"; }
error() { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

# ── 检查 Docker 网络是否存在 ────────────────────────────────
ensure_network() {
    if ! docker network inspect "${NETWORK}" >/dev/null 2>&1; then
        warn "Docker 网络 ${NETWORK} 不存在，正在创建..."
        docker network create "${NETWORK}"
    fi
}

start() {
    # 已存在则提示
    if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
        if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
            warn "obbinlog 容器已在运行中"
            status
            return 0
        else
            info "发现已停止的同名容器，先删除..."
            docker rm -f "${CONTAINER_NAME}" >/dev/null
        fi
    fi

    if [ ! -f "${ENTRYPOINT_SCRIPT}" ]; then
        error "找不到入口脚本: ${ENTRYPOINT_SCRIPT}"
        exit 1
    fi

    ensure_network

    info "启动 obbinlog 容器..."
    info "  镜像: ${IMAGE}"
    info "  网络: ${NETWORK}"
    info "  端口: ${PORT}:2983"
    info "  OB host 映射: ${OB_HOST_ENTRY}"

    docker run -d \
        --name "${CONTAINER_NAME}" \
        --network "${NETWORK}" \
        --add-host "${OB_HOST_ENTRY}" \
        -e OB_SYS_USERNAME="${OB_SYS_USERNAME}" \
        -e OB_SYS_PASSWORD="${OB_SYS_PASSWORD}" \
        -p "${PORT}:2983" \
        --restart unless-stopped \
        --entrypoint "" \
        -v "${ENTRYPOINT_SCRIPT}:/cdc-entrypoint.sh:ro" \
        "${IMAGE}" \
        bash /cdc-entrypoint.sh

    info "obbinlog 已启动，等待初始化..."
    sleep 5
    status
}

stop() {
    if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
        info "停止并删除 obbinlog 容器..."
        docker rm -f "${CONTAINER_NAME}" >/dev/null
        info "obbinlog 已停止"
    else
        warn "obbinlog 容器不存在"
    fi
}

restart() {
    stop
    sleep 2
    start
}

status() {
    if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
        local st
        st=$(docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Status}}')
        info "obbinlog 运行中: ${st}"
        # 检查 logproxy 进程和端口监听
        if docker exec "${CONTAINER_NAME}" sh -c "ps aux 2>/dev/null | grep -q '[l]ogproxy'" 2>/dev/null; then
            info "  logproxy 进程: 运行中"
        else
            warn "  logproxy 进程: 未检测到（可能仍在初始化）"
        fi
    else
        warn "obbinlog 未运行"
        return 1
    fi
}

logs() {
    if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
        docker logs -f "${CONTAINER_NAME}"
    else
        error "obbinlog 容器不存在"
        exit 1
    fi
}

case "${1:-}" in
    start)   start   ;;
    stop)    stop    ;;
    restart) restart ;;
    status)  status  ;;
    logs)    logs    ;;
    *)
        echo "用法: $0 {start|stop|restart|status|logs}"
        echo ""
        echo "  start    启动 obbinlog 容器（CDC 模式）"
        echo "  stop     停止并删除 obbinlog 容器"
        echo "  restart  重启 obbinlog 容器"
        echo "  status   查看运行状态"
        echo "  logs     查看实时日志"
        echo ""
        echo "可配置环境变量:"
        echo "  OB_SYS_USERNAME          OB sys 用户名（默认 root）"
        echo "  OB_SYS_PASSWORD          OB sys 密码（默认 password）"
        echo "  OBBINLOG_OB_HOST_ENTRY   OB 主机映射（默认 centos-ob:172.22.0.1）"
        echo "  OBBINLOG_PORT            映射端口（默认 2983）"
        echo "  OBBINLOG_IMAGE           镜像（默认 oceanbase/obbinlog-ce:4.2.5-test）"
        echo "  OBBINLOG_NETWORK         Docker 网络（默认 flink-network）"
        exit 1
        ;;
esac
