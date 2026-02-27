#!/bin/bash
# Flink 高可用部署脚本
# 按照 docs/FLINK_HA_DEPLOYMENT_GUIDE.md 中的步骤自动部署

set -e

echo "=========================================="
echo "Flink 高可用（HA）部署脚本"
echo "时间: $(date)"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印函数
print_step() {
    echo -e "${GREEN}[步骤 $1]${NC} $2"
}

print_info() {
    echo -e "${YELLOW}[信息]${NC} $1"
}

print_error() {
    echo -e "${RED}[错误]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[成功]${NC} $1"
}

# 检查命令是否存在
check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 未安装，请先安装"
        exit 1
    fi
}

# 步骤 1: 检查前置条件
print_step "1" "检查前置条件"
check_command docker
check_command docker-compose
print_success "Docker 和 Docker Compose 已安装"
echo ""

# 步骤 2: 检查 .env 文件
print_step "2" "检查环境变量配置"
if [ ! -f .env ]; then
    print_error ".env 文件不存在"
    print_info "请从 .env.example 复制并配置"
    exit 1
fi

# 检查 HA 配置
if grep -q "HA_MODE=zookeeper" .env; then
    print_success "HA 模式已启用"
else
    print_error "HA 模式未启用，请在 .env 中设置 HA_MODE=zookeeper"
    exit 1
fi
echo ""

# 步骤 3: 停止现有服务
print_step "3" "停止现有服务"
print_info "正在停止所有容器..."
docker-compose down
print_success "所有容器已停止"
echo ""

# 步骤 4: 清理旧的 HA 数据（可选）
print_step "4" "清理旧的 HA 数据"
read -p "是否清理旧的 HA 数据？这将删除 flink-ha volume (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_info "删除旧的 HA 数据..."
    docker volume rm flink-ha 2>/dev/null || true
    docker volume create flink-ha
    print_success "HA 数据已清理"
else
    print_info "保留现有 HA 数据"
fi
echo ""

# 步骤 5: 启动 ZooKeeper
print_step "5" "启动 ZooKeeper"
print_info "正在启动 ZooKeeper..."
docker-compose up -d zookeeper

print_info "等待 ZooKeeper 启动（30秒）..."
sleep 30

# 检查 ZooKeeper 状态
if docker-compose ps zookeeper | grep -q "Up"; then
    print_success "ZooKeeper 已启动"
else
    print_error "ZooKeeper 启动失败"
    docker-compose logs zookeeper
    exit 1
fi
echo ""

# 步骤 6: 启动 JobManager（主和备）
print_step "6" "启动 JobManager（主和备）"
print_info "正在启动两个 JobManager..."
docker-compose up -d jobmanager jobmanager-standby

print_info "等待 JobManager 启动（60秒）..."
sleep 60

# 检查 JobManager 状态
if docker-compose ps jobmanager | grep -q "Up"; then
    print_success "JobManager 主节点已启动"
else
    print_error "JobManager 主节点启动失败"
    docker-compose logs jobmanager
    exit 1
fi

if docker-compose ps jobmanager-standby | grep -q "Up"; then
    print_success "JobManager 备节点已启动"
else
    print_error "JobManager 备节点启动失败"
    docker-compose logs jobmanager-standby
    exit 1
fi
echo ""

# 步骤 7: 启动 TaskManager
print_step "7" "启动 TaskManager"
print_info "正在启动 3 个 TaskManager..."
docker-compose up -d --scale taskmanager=3

print_info "等待 TaskManager 启动（30秒）..."
sleep 30

# 检查 TaskManager 数量
TASKMANAGER_COUNT=$(docker-compose ps taskmanager | grep -c "Up" || true)
if [ "$TASKMANAGER_COUNT" -ge 3 ]; then
    print_success "已启动 $TASKMANAGER_COUNT 个 TaskManager"
else
    print_error "只启动了 $TASKMANAGER_COUNT 个 TaskManager（期望 3 个）"
fi
echo ""

# 步骤 8: 验证集群状态
print_step "8" "验证集群状态"
print_info "检查所有容器状态..."
docker-compose ps
echo ""

# 检查 Leader
print_info "检查 JobManager Leader..."
sleep 5

if curl -s http://localhost:8081/overview >/dev/null 2>&1; then
    print_success "Leader: JobManager 主节点 (端口 8081)"
    LEADER_PORT=8081
elif curl -s http://localhost:8082/overview >/dev/null 2>&1; then
    print_success "Leader: JobManager 备节点 (端口 8082)"
    LEADER_PORT=8082
else
    print_error "无法连接到任何 JobManager"
    exit 1
fi
echo ""

# 显示集群资源
print_info "集群资源信息:"
curl -s http://localhost:$LEADER_PORT/overview | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(f'  Flink 版本: {data.get(\"flink-version\", \"N/A\")}')
    print(f'  TaskManagers: {data.get(\"taskmanagers\", 0)}')
    print(f'  总槽位: {data.get(\"slots-total\", 0)}')
    print(f'  可用槽位: {data.get(\"slots-available\", 0)}')
except:
    print('  无法解析集群信息')
" 2>/dev/null || echo "  无法获取集群信息"
echo ""

# 步骤 9: 显示访问信息
print_step "9" "部署完成"
echo ""
print_success "Flink 高可用集群已成功部署！"
echo ""
echo "访问信息:"
echo "  主 JobManager Web UI:   http://localhost:8081"
echo "  备 JobManager Web UI:   http://localhost:8082"
echo "  当前 Leader:            http://localhost:$LEADER_PORT"
echo ""
echo "下一步操作:"
echo "  1. 编译应用程序: mvn clean package -DskipTests"
echo "  2. 提交作业: 访问 http://localhost:$LEADER_PORT 并上传 JAR"
echo "  3. 查看日志: docker-compose logs -f jobmanager"
echo "  4. 监控集群: ./shell/monitor-ha-cluster.sh"
echo ""
echo "测试故障转移:"
echo "  1. 停止主节点: docker-compose stop jobmanager"
echo "  2. 等待 30 秒"
echo "  3. 检查备节点: curl http://localhost:8082/overview"
echo ""
echo "详细文档: docs/FLINK_HA_DEPLOYMENT_GUIDE.md"
echo ""
print_success "部署脚本执行完成！"
echo "=========================================="
