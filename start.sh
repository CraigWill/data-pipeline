#!/bin/bash

# 实时数据管道启动脚本
# 用于启动所有服务或单独启动指定服务
# 支持私有 Flink 镜像构建

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 可用服务列表
SERVICES="zookeeper jobmanager jobmanager-standby taskmanager monitor-backend monitor-frontend"

# 显示帮助
show_help() {
    echo "用法: ./start.sh [选项] [服务名...]"
    echo ""
    echo "选项:"
    echo "  --skip-build       跳过构建步骤"
    echo "  --build-only       只构建不启动"
    echo "  --rebuild-flink    强制重新构建 Flink 镜像（无缓存）"
    echo "  --stop             停止服务"
    echo "  --restart          重启服务"
    echo "  --status           查看服务状态"
    echo "  --logs             查看服务日志"
    echo "  --clean            清理所有容器和数据卷"
    echo "  --help, -h         显示帮助信息"
    echo ""
    echo "可用服务:"
    echo "  all                所有服务（默认）"
    echo "  backend            后端服务 (monitor-backend)"
    echo "  frontend           前端服务 (monitor-frontend)"
    echo "  flink              Flink 集群 (jobmanager + taskmanager)"
    echo "  jobmanager         JobManager (主节点)"
    echo "  taskmanager        TaskManager (工作节点)"
    echo "  zookeeper          ZooKeeper"
    echo ""
    echo "示例:"
    echo "  ./start.sh                       # 启动所有服务"
    echo "  ./start.sh backend               # 只启动后端"
    echo "  ./start.sh frontend backend      # 启动前端和后端"
    echo "  ./start.sh --restart backend     # 重启后端"
    echo "  ./start.sh --rebuild-flink       # 重新构建 Flink 镜像并启动"
    echo "  ./start.sh --stop                # 停止所有服务"
    echo "  ./start.sh --logs backend        # 查看后端日志"
    echo "  ./start.sh --clean               # 清理所有容器和数据"
}

# 检查 Docker
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}错误: Docker 未运行${NC}"
        exit 1
    fi
}

# 检查 Docker Compose
check_docker_compose() {
    if ! docker-compose version > /dev/null 2>&1; then
        echo -e "${RED}错误: Docker Compose 未安装${NC}"
        exit 1
    fi
}

# 检查 Oracle 网络连接
check_oracle_network() {
    local oracle_container="oracle11g"
    local flink_network="flink-network"
    
    # 检查 Oracle 容器是否运行
    if docker ps --format '{{.Names}}' | grep -q "^${oracle_container}$"; then
        # 检查 Flink 网络是否存在
        if docker network ls --format '{{.Name}}' | grep -q "^${flink_network}$"; then
            # 检查 Oracle 是否在 Flink 网络中
            if ! docker network inspect ${flink_network} --format '{{range .Containers}}{{.Name}}{{"\n"}}{{end}}' 2>/dev/null | grep -q "^${oracle_container}$"; then
                echo -e "${YELLOW}将 ${oracle_container} 连接到 ${flink_network}...${NC}"
                docker network connect ${flink_network} ${oracle_container} 2>/dev/null || true
                echo -e "${GREEN}✓ 网络连接成功${NC}"
            fi
        fi
    fi
}

# 检查 .env 文件
check_env() {
    if [ ! -f .env ]; then
        if [ -f .env.example ]; then
            cp .env.example .env
            echo -e "${YELLOW}已从 .env.example 创建 .env${NC}"
        else
            echo -e "${YELLOW}警告: 未找到 .env 文件${NC}"
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
                services="$services jobmanager jobmanager-standby taskmanager"
                ;;
            jobmanager)
                services="$services jobmanager jobmanager-standby"
                ;;
            taskmanager)
                services="$services taskmanager"
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

# 构建后端 JAR
build_backend() {
    echo -e "${BLUE}>>> 构建后端 JAR 包...${NC}"
    
    if [ ! -f "pom.xml" ]; then
        echo -e "${RED}错误: 未找到 pom.xml${NC}"
        exit 1
    fi
    
    mvn clean package -DskipTests
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 后端 JAR 构建完成${NC}"
        ls -lh monitor-backend/target/monitor-backend-*.jar
    else
        echo -e "${RED}✗ 后端 JAR 构建失败${NC}"
        exit 1
    fi
}

# 构建 Flink 私有镜像
build_flink_images() {
    local no_cache="$1"
    echo -e "${BLUE}>>> 构建 Flink 私有镜像...${NC}"
    
    # 检查 JAR 文件
    if ! ls monitor-backend/target/monitor-backend-*.jar 1> /dev/null 2>&1; then
        echo -e "${YELLOW}未找到 Monitor Backend JAR 文件，先构建...${NC}"
        build_backend
    fi
    
    if ! ls flink-jobs/target/flink-jobs-*.jar 1> /dev/null 2>&1; then
        echo -e "${YELLOW}未找到 Flink Jobs JAR 文件，先构建...${NC}"
        build_backend
    fi
    
    local build_args=""
    if [ "$no_cache" = "true" ]; then
        build_args="--no-cache"
        echo -e "${YELLOW}使用 --no-cache 强制重新构建${NC}"
    fi
    
    # 构建 JobManager
    echo -e "${BLUE}  - 构建 JobManager 镜像...${NC}"
    docker build $build_args -f docker/jobmanager/Dockerfile -t flink-jobmanager:latest .
    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ JobManager 镜像构建失败${NC}"
        exit 1
    fi
    
    # 构建 TaskManager
    echo -e "${BLUE}  - 构建 TaskManager 镜像...${NC}"
    docker build $build_args -f docker/taskmanager/Dockerfile -t flink-taskmanager:latest .
    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ TaskManager 镜像构建失败${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Flink 镜像构建完成${NC}"
    docker images | grep -E "REPOSITORY|flink-(jobmanager|taskmanager)"
}

# 构建 Docker 镜像
build_images() {
    local services="$1"
    local rebuild_flink="$2"
    
    echo -e "${BLUE}>>> 构建 Docker 镜像...${NC}"
    
    # 如果需要重建 Flink 或服务包含 Flink 组件
    if [ "$rebuild_flink" = "true" ] || echo "$services" | grep -qE "jobmanager|taskmanager|flink"; then
        build_flink_images "$rebuild_flink"
    fi
    
    # 构建其他服务
    if [ -z "$services" ]; then
        docker-compose build
    else
        # 过滤掉 jobmanager 和 taskmanager（已经单独构建）
        local other_services=$(echo "$services" | sed 's/jobmanager//g' | sed 's/taskmanager//g')
        if [ -n "$other_services" ]; then
            docker-compose build $other_services
        fi
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
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 服务已启动${NC}"
    else
        echo -e "${RED}✗ 服务启动失败${NC}"
        exit 1
    fi
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

# 清理环境
clean_environment() {
    echo -e "${YELLOW}警告: 这将删除所有容器、网络和数据卷${NC}"
    read -p "确认继续? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}>>> 清理环境...${NC}"
        docker-compose down -v
        echo -e "${GREEN}✓ 环境已清理${NC}"
    else
        echo -e "${YELLOW}已取消${NC}"
    fi
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
    echo -e "  前端界面:        ${GREEN}http://localhost:8888${NC}"
    echo -e "  后端 API:        ${GREEN}http://localhost:5001${NC}"
    echo -e "  Flink 控制台:    ${GREEN}http://localhost:8081${NC}"
    echo -e "  Flink 备用节点:  ${GREEN}http://localhost:8082${NC}"
    echo ""
    echo "=========================================="
    echo "  镜像信息"
    echo "=========================================="
    docker images | grep -E "REPOSITORY|flink-(jobmanager|taskmanager)|monitor"
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
    local rebuild_flink=false
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
            --rebuild-flink)
                rebuild_flink=true
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
            --clean)
                action="clean"
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
    check_docker_compose
    check_env
    check_oracle_network
    
    echo -e "${GREEN}=== 实时数据管道启动脚本 ===${NC}"
    echo ""
    
    case $action in
        start)
            if [ "$skip_build" = false ]; then
                build_backend
                build_images "$target_services" "$rebuild_flink"
            fi
            if [ "$build_only" = false ]; then
                start_services "$target_services"
                sleep 3
                show_status
            fi
            ;;
        stop)
            stop_services "$target_services"
            ;;
        restart)
            restart_services "$target_services"
            sleep 3
            show_status
            ;;
        status)
            show_status
            ;;
        logs)
            show_logs "$target_services"
            ;;
        clean)
            clean_environment
            ;;
    esac
}

main "$@"
