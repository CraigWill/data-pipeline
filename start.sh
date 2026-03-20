#!/bin/bash

# 实时数据管道启动脚本
# 用于启动所有服务或单独启动指定服务

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 可用服务列表
SERVICES="zookeeper flink-jobmanager flink-jobmanager-standby flink-taskmanager monitor-backend monitor-frontend"

# 显示帮助
show_help() {
    echo "用法: ./start.sh [选项] [服务名...]"
    echo ""
    echo "选项:"
    echo "  --skip-build    跳过构建步骤"
    echo "  --build-only    只构建不启动"
    echo "  --stop          停止服务"
    echo "  --restart       重启服务"
    echo "  --status        查看服务状态"
    echo "  --logs          查看服务日志"
    echo "  --help, -h      显示帮助信息"
    echo ""
    echo "可用服务:"
    echo "  all             所有服务（默认）"
    echo "  backend         后端服务 (monitor-backend)"
    echo "  frontend        前端服务 (monitor-frontend)"
    echo "  flink           Flink 集群 (jobmanager + taskmanager)"
    echo "  zookeeper       ZooKeeper"
    echo ""
    echo "示例:"
    echo "  ./start.sh                    # 启动所有服务"
    echo "  ./start.sh backend            # 只启动后端"
    echo "  ./start.sh frontend backend   # 启动前端和后端"
    echo "  ./start.sh --restart backend  # 重启后端"
    echo "  ./start.sh --stop             # 停止所有服务"
    echo "  ./start.sh --logs backend     # 查看后端日志"
}

# 检查 Docker
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}错误: Docker 未运行${NC}"
        exit 1
    fi
}

# 检查 .env 文件
check_env() {
    if [ ! -f .env ]; then
        if [ -f .env.example ]; then
            cp .env.example .env
            echo -e "${YELLOW}已从 .env.example 创建 .env${NC}"
        fi
    fi
}

# 解析服务名
parse_services() {
    local services=""
    for svc in "$@"; do
        case $svc in
            all)
                services="$SERVICES"
                ;;
            backend)
                services="$services monitor-backend"
                ;;
            frontend)
                services="$services monitor-frontend"
                ;;
            flink)
                services="$services flink-jobmanager flink-jobmanager-standby flink-taskmanager"
                ;;
            zookeeper)
                services="$services zookeeper"
                ;;
            *)
                # 直接使用服务名
                services="$services $svc"
                ;;
        esac
    done
    echo $services
}

# 构建后端
build_backend() {
    echo -e "${BLUE}>>> 构建后端...${NC}"
    mvn clean package -DskipTests -q
    echo -e "${GREEN}✓ 后端构建完成${NC}"
}

# 构建 Docker 镜像
build_images() {
    local services="$1"
    echo -e "${BLUE}>>> 构建 Docker 镜像...${NC}"
    if [ -z "$services" ]; then
        docker-compose build --quiet
    else
        docker-compose build --quiet $services
    fi
    echo -e "${GREEN}✓ 镜像构建完成${NC}"
}

# 启动服务
start_services() {
    local services="$1"
    echo -e "${BLUE}>>> 启动服务...${NC}"
    if [ -z "$services" ]; then
        docker-compose up -d
    else
        docker-compose up -d $services
    fi
    echo -e "${GREEN}✓ 服务已启动${NC}"
}

# 停止服务
stop_services() {
    local services="$1"
    echo -e "${BLUE}>>> 停止服务...${NC}"
    if [ -z "$services" ]; then
        docker-compose down
    else
        docker-compose stop $services
    fi
    echo -e "${GREEN}✓ 服务已停止${NC}"
}

# 重启服务
restart_services() {
    local services="$1"
    echo -e "${BLUE}>>> 重启服务...${NC}"
    if [ -z "$services" ]; then
        docker-compose restart
    else
        docker-compose restart $services
    fi
    echo -e "${GREEN}✓ 服务已重启${NC}"
}

# 查看状态
show_status() {
    echo ""
    echo "=========================================="
    echo "  服务状态"
    echo "=========================================="
    docker-compose ps
    echo ""
    echo "=========================================="
    echo "  访问地址"
    echo "=========================================="
    echo -e "  前端界面:     ${GREEN}http://localhost:8888${NC}"
    echo -e "  后端 API:     ${GREEN}http://localhost:5001${NC}"
    echo -e "  Flink 控制台: ${GREEN}http://localhost:8081${NC}"
    echo ""
}

# 查看日志
show_logs() {
    local services="$1"
    if [ -z "$services" ]; then
        docker-compose logs -f --tail=100
    else
        docker-compose logs -f --tail=100 $services
    fi
}

# 主流程
main() {
    local action="start"
    local skip_build=false
    local build_only=false
    local target_services=""
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                show_help
                exit 0
                ;;
            --skip-build)
                skip_build=true
                shift
                ;;
            --build-only)
                build_only=true
                shift
                ;;
            --stop)
                action="stop"
                shift
                ;;
            --restart)
                action="restart"
                shift
                ;;
            --status)
                action="status"
                shift
                ;;
            --logs)
                action="logs"
                shift
                ;;
            *)
                target_services="$target_services $1"
                shift
                ;;
        esac
    done
    
    # 解析服务名
    if [ -n "$target_services" ]; then
        target_services=$(parse_services $target_services)
    fi
    
    check_docker
    check_env
    
    case $action in
        start)
            if [ "$skip_build" = false ]; then
                build_backend
                build_images "$target_services"
            fi
            if [ "$build_only" = false ]; then
                start_services "$target_services"
                show_status
            fi
            ;;
        stop)
            stop_services "$target_services"
            ;;
        restart)
            restart_services "$target_services"
            show_status
            ;;
        status)
            show_status
            ;;
        logs)
            show_logs "$target_services"
            ;;
    esac
}

main "$@"
