#!/bin/bash
# 生产级 Flink HA 部署脚本
# 包含完整的预检查、部署、验证流程

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║           生产级 Flink HA 部署脚本                             ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================================================
# 阶段 1: 预检查
# ============================================================================
echo -e "${YELLOW}阶段 1: 预检查${NC}"
echo "----------------------------------------"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗ Docker 未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker 已安装${NC}"

# 检查 Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}✗ Docker Compose 未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker Compose 已安装${NC}"

# 检查 JAR 文件
if [ ! -f "target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar" ]; then
    echo -e "${YELLOW}⚠ JAR 文件不存在，开始编译...${NC}"
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ 编译失败${NC}"
        exit 1
    fi
fi
echo -e "${GREEN}✓ JAR 文件存在${NC}"

# 检查配置文件
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}⚠ .env 文件不存在，从模板创建...${NC}"
    cp .env.example .env
fi
echo -e "${GREEN}✓ 配置文件存在${NC}"

echo ""

# ============================================================================
# 阶段 2: 停止旧服务
# ============================================================================
echo -e "${YELLOW}阶段 2: 停止旧服务${NC}"
echo "----------------------------------------"

if docker ps | grep -q "flink-jobmanager\|flink-taskmanager"; then
    echo "停止现有 Flink 服务..."
    docker-compose down
    echo -e "${GREEN}✓ 旧服务已停止${NC}"
else
    echo -e "${GREEN}✓ 没有运行中的服务${NC}"
fi

echo ""

# ============================================================================
# 阶段 3: 清理和准备
# ============================================================================
echo -e "${YELLOW}阶段 3: 清理和准备${NC}"
echo "----------------------------------------"

# 创建必要的目录
echo "创建输出目录..."
mkdir -p output/cdc
mkdir -p logs
echo -e "${GREEN}✓ 目录已创建${NC}"

# 清理旧的 checkpoint（可选）
read -p "是否清理旧的 checkpoint? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "清理 checkpoint..."
    docker volume rm flink-checkpoints 2>/dev/null || true
    docker volume rm flink-savepoints 2>/dev/null || true
    docker volume rm flink-ha 2>/dev/null || true
    echo -e "${GREEN}✓ Checkpoint 已清理${NC}"
fi

echo ""

# ============================================================================
# 阶段 4: 启动服务
# ============================================================================
echo -e "${YELLOW}阶段 4: 启动服务${NC}"
echo "----------------------------------------"

# 设置 HA 模式
export HA_MODE=zookeeper

# 启动 ZooKeeper
echo "启动 ZooKeeper..."
docker-compose up -d zookeeper
sleep 10

# 检查 ZooKeeper 健康状态
if docker ps | grep -q "zookeeper.*healthy"; then
    echo -e "${GREEN}✓ ZooKeeper 启动成功${NC}"
else
    echo -e "${RED}✗ ZooKeeper 启动失败${NC}"
    exit 1
fi

# 启动 JobManager（主 + 备）
echo "启动 JobManager..."
docker-compose up -d jobmanager jobmanager-standby
sleep 20

# 检查 JobManager 健康状态
MAIN_HEALTHY=$(docker ps | grep "flink-jobmanager.*healthy" | wc -l)
if [ $MAIN_HEALTHY -eq 2 ]; then
    echo -e "${GREEN}✓ 两个 JobManager 启动成功${NC}"
else
    echo -e "${YELLOW}⚠ 只有 $MAIN_HEALTHY 个 JobManager 健康${NC}"
fi

# 启动 TaskManager
echo "启动 TaskManager..."
docker-compose up -d --scale taskmanager=3
sleep 15

# 检查 TaskManager 健康状态
TM_COUNT=$(docker ps | grep "taskmanager.*healthy" | wc -l)
echo -e "${GREEN}✓ $TM_COUNT 个 TaskManager 启动成功${NC}"

echo ""

# ============================================================================
# 阶段 5: 部署作业
# ============================================================================
echo -e "${YELLOW}阶段 5: 部署作业${NC}"
echo "----------------------------------------"

# 复制 JAR 到容器
echo "复制 JAR 文件到 JobManager..."
docker cp target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar \
    flink-jobmanager:/opt/flink/usrlib/realtime-data-pipeline.jar

# 等待 JobManager 完全就绪
echo "等待 JobManager 就绪..."
for i in {1..30}; do
    if curl -s http://localhost:8081/overview &>/dev/null; then
        echo -e "${GREEN}✓ JobManager 就绪${NC}"
        break
    fi
    echo -ne "  等待中... ($i/30)\r"
    sleep 2
done

# 提交作业
echo "提交 Flink 作业..."
JOB_OUTPUT=$(docker exec flink-jobmanager flink run -d \
    -c com.realtime.pipeline.FlinkCDC3App \
    /opt/flink/usrlib/realtime-data-pipeline.jar 2>&1)

if echo "$JOB_OUTPUT" | grep -q "Job has been submitted"; then
    JOB_ID=$(echo "$JOB_OUTPUT" | grep -oP 'JobID \K[a-f0-9]+')
    echo -e "${GREEN}✓ 作业提交成功${NC}"
    echo -e "  作业 ID: ${CYAN}$JOB_ID${NC}"
else
    echo -e "${RED}✗ 作业提交失败${NC}"
    echo "$JOB_OUTPUT"
    exit 1
fi

echo ""

# ============================================================================
# 阶段 6: 验证部署
# ============================================================================
echo -e "${YELLOW}阶段 6: 验证部署${NC}"
echo "----------------------------------------"

sleep 10

# 检查作业状态
echo "检查作业状态..."
JOB_STATE=$(curl -s http://localhost:8081/jobs/$JOB_ID | jq -r '.state' 2>/dev/null)
if [ "$JOB_STATE" = "RUNNING" ]; then
    echo -e "${GREEN}✓ 作业运行中${NC}"
else
    echo -e "${YELLOW}⚠ 作业状态: $JOB_STATE${NC}"
fi

# 检查集群资源
echo "检查集群资源..."
OVERVIEW=$(curl -s http://localhost:8081/overview)
TM_COUNT=$(echo "$OVERVIEW" | jq -r '.["taskmanagers"]')
SLOTS_TOTAL=$(echo "$OVERVIEW" | jq -r '.["slots-total"]')
SLOTS_AVAILABLE=$(echo "$OVERVIEW" | jq -r '.["slots-available"]')

echo -e "  TaskManagers: ${CYAN}$TM_COUNT${NC}"
echo -e "  总槽位: ${CYAN}$SLOTS_TOTAL${NC}"
echo -e "  可用槽位: ${CYAN}$SLOTS_AVAILABLE${NC}"

# 检查 HA 状态
echo "检查 HA 状态..."
if curl -s http://localhost:8082/overview &>/dev/null; then
    echo -e "${GREEN}✓ 备用 JobManager 在线${NC}"
else
    echo -e "${YELLOW}⚠ 备用 JobManager 离线${NC}"
fi

echo ""

# ============================================================================
# 阶段 7: 启动监控
# ============================================================================
echo -e "${YELLOW}阶段 7: 启动监控${NC}"
echo "----------------------------------------"

read -p "是否启动健康监控? (Y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    echo "启动健康监控..."
    chmod +x shell/production-health-monitor.sh
    nohup ./shell/production-health-monitor.sh > logs/health-monitor.out 2>&1 &
    MONITOR_PID=$!
    echo -e "${GREEN}✓ 健康监控已启动 (PID: $MONITOR_PID)${NC}"
    echo "  日志文件: logs/health-monitor.log"
    echo "  输出文件: logs/health-monitor.out"
fi

echo ""

# ============================================================================
# 部署总结
# ============================================================================
echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                    部署成功                                    ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}访问地址:${NC}"
echo -e "  主 JobManager:  http://localhost:8081"
echo -e "  备 JobManager:  http://localhost:8082"
echo ""
echo -e "${GREEN}作业信息:${NC}"
echo -e "  作业 ID:        $JOB_ID"
echo -e "  作业状态:       $JOB_STATE"
echo ""
echo -e "${GREEN}监控命令:${NC}"
echo -e "  查看日志:       docker logs -f flink-jobmanager"
echo -e "  查看作业:       curl http://localhost:8081/jobs/overview | jq"
echo -e "  查看监控:       tail -f logs/health-monitor.log"
echo ""
echo -e "${GREEN}管理命令:${NC}"
echo -e "  停止服务:       docker-compose down"
echo -e "  查看容器:       docker ps"
echo -e "  故障转移测试:   ./shell/test-ha-simple.sh"
echo ""
