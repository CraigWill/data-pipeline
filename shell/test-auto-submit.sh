#!/bin/bash
# 测试自动作业提交功能

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║           测试自动作业提交功能                                 ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# 检查配置
echo -e "${YELLOW}步骤 1: 检查配置${NC}"
echo "----------------------------------------"

if grep -q "AUTO_SUBMIT_JOB=true" .env; then
    echo -e "${GREEN}✓ 自动提交已启用${NC}"
else
    echo -e "${RED}✗ 自动提交未启用${NC}"
    echo "请在 .env 中设置: AUTO_SUBMIT_JOB=true"
    exit 1
fi

DELAY=$(grep "AUTO_SUBMIT_DELAY" .env | cut -d'=' -f2)
echo -e "  延迟时间: ${CYAN}${DELAY}秒${NC}"
echo ""

# 停止现有服务
echo -e "${YELLOW}步骤 2: 停止现有服务${NC}"
echo "----------------------------------------"
docker-compose down
echo -e "${GREEN}✓ 服务已停止${NC}"
echo ""

# 启动服务
echo -e "${YELLOW}步骤 3: 启动服务${NC}"
echo "----------------------------------------"
echo "启动 ZooKeeper..."
docker-compose up -d zookeeper
sleep 10

echo "启动 JobManager..."
docker-compose up -d jobmanager
echo -e "${GREEN}✓ JobManager 已启动${NC}"
echo ""

# 监控自动提交过程
echo -e "${YELLOW}步骤 4: 监控自动提交过程${NC}"
echo "----------------------------------------"
echo "等待 ${DELAY} 秒让自动提交脚本执行..."
echo ""

# 实时显示 JobManager 日志
echo "JobManager 日志:"
echo "----------------------------------------"
docker logs -f flink-jobmanager 2>&1 | grep --line-buffered "Auto-Submit" &
LOG_PID=$!

# 等待指定时间 + 额外30秒
TOTAL_WAIT=$((DELAY + 30))
for ((i=1; i<=TOTAL_WAIT; i++)); do
    echo -ne "\r等待中... $i/${TOTAL_WAIT}秒"
    sleep 1
done
echo ""

# 停止日志监控
kill $LOG_PID 2>/dev/null || true
echo ""

# 验证作业状态
echo -e "${YELLOW}步骤 5: 验证作业状态${NC}"
echo "----------------------------------------"

# 检查 JobManager 是否就绪
if curl -s http://localhost:8081/overview > /dev/null 2>&1; then
    echo -e "${GREEN}✓ JobManager 就绪${NC}"
else
    echo -e "${RED}✗ JobManager 未就绪${NC}"
    exit 1
fi

# 检查作业状态
JOBS=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null)
RUNNING_JOBS=$(echo "$JOBS" | jq -r '.jobs[] | select(.state == "RUNNING") | .jid' 2>/dev/null)

if [ -n "$RUNNING_JOBS" ]; then
    echo -e "${GREEN}✓ 作业已自动提交并运行${NC}"
    echo ""
    echo "运行中的作业:"
    echo "$JOBS" | jq -r '.jobs[] | select(.state == "RUNNING") | "  - ID: \(.jid)\n    名称: \(.name)\n    状态: \(.state)"'
else
    echo -e "${RED}✗ 没有运行中的作业${NC}"
    echo ""
    echo "所有作业:"
    echo "$JOBS" | jq '.'
fi
echo ""

# 检查自动提交日志
echo -e "${YELLOW}步骤 6: 检查自动提交日志${NC}"
echo "----------------------------------------"
if docker exec flink-jobmanager test -f /opt/flink/logs/auto-submit.log; then
    echo "自动提交日志:"
    docker exec flink-jobmanager cat /opt/flink/logs/auto-submit.log
else
    echo -e "${YELLOW}⚠ 自动提交日志文件不存在${NC}"
fi
echo ""

# 启动 TaskManager
echo -e "${YELLOW}步骤 7: 启动 TaskManager${NC}"
echo "----------------------------------------"
docker-compose up -d --scale taskmanager=3
sleep 15
echo -e "${GREEN}✓ TaskManager 已启动${NC}"
echo ""

# 最终验证
echo -e "${YELLOW}步骤 8: 最终验证${NC}"
echo "----------------------------------------"

# 检查集群状态
OVERVIEW=$(curl -s http://localhost:8081/overview)
TM_COUNT=$(echo "$OVERVIEW" | jq -r '.["taskmanagers"]')
SLOTS_TOTAL=$(echo "$OVERVIEW" | jq -r '.["slots-total"]')
SLOTS_AVAILABLE=$(echo "$OVERVIEW" | jq -r '.["slots-available"]')

echo "集群状态:"
echo "  TaskManagers: $TM_COUNT"
echo "  总槽位: $SLOTS_TOTAL"
echo "  可用槽位: $SLOTS_AVAILABLE"
echo ""

# 再次检查作业状态
FINAL_JOBS=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null)
FINAL_RUNNING=$(echo "$FINAL_JOBS" | jq -r '.jobs[] | select(.state == "RUNNING") | .jid' 2>/dev/null)

if [ -n "$FINAL_RUNNING" ]; then
    JOB_ID=$(echo "$FINAL_RUNNING" | head -1)
    JOB_INFO=$(curl -s http://localhost:8081/jobs/$JOB_ID 2>/dev/null)
    JOB_STATE=$(echo "$JOB_INFO" | jq -r '.state')
    
    echo "作业详情:"
    echo "  ID: $JOB_ID"
    echo "  状态: $JOB_STATE"
    echo ""
fi

# 测试结果
echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                    测试结果                                    ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [ -n "$FINAL_RUNNING" ] && [ "$JOB_STATE" = "RUNNING" ]; then
    echo -e "${GREEN}✓ 测试成功：作业已自动提交并运行${NC}"
    echo ""
    echo "验证项:"
    echo "  ✓ JobManager 启动成功"
    echo "  ✓ 自动提交脚本执行"
    echo "  ✓ 作业成功提交"
    echo "  ✓ 作业状态为 RUNNING"
    echo "  ✓ TaskManager 连接成功"
    echo ""
    echo "访问地址:"
    echo "  Web UI: http://localhost:8081"
    echo "  作业详情: http://localhost:8081/#/job/$JOB_ID/overview"
else
    echo -e "${RED}✗ 测试失败：作业未能自动提交或运行${NC}"
    echo ""
    echo "请检查:"
    echo "  1. docker logs flink-jobmanager"
    echo "  2. docker exec flink-jobmanager cat /opt/flink/logs/auto-submit.log"
    echo "  3. curl http://localhost:8081/jobs/overview | jq"
fi

echo ""
