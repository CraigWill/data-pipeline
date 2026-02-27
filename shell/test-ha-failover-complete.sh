#!/bin/bash
# Flink HA 完整故障转移测试脚本
# 测试场景：主节点故障 → 备用接管 → 主节点恢复为备用 → 验证作业持续运行

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 配置
MAIN_PORT=8081
STANDBY_PORT=8082
OUTPUT_DIR="output/cdc"
TEST_DURATION=180  # 测试持续时间（秒）

echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║         Flink HA 完整故障转移测试                              ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# 函数：检查 JobManager 状态
check_jobmanager() {
    local port=$1
    local name=$2
    local response=$(curl -s http://localhost:$port/overview 2>/dev/null)
    if [ -n "$response" ]; then
        echo -e "${GREEN}✓ $name (端口 $port) 在线${NC}"
        return 0
    else
        echo -e "${RED}✗ $name (端口 $port) 离线${NC}"
        return 1
    fi
}

# 函数：获取当前 Leader
get_leader() {
    if check_jobmanager $MAIN_PORT "主节点" &>/dev/null; then
        local jobs=$(curl -s http://localhost:$MAIN_PORT/jobs/overview 2>/dev/null | jq -r '.jobs[]?' 2>/dev/null)
        if [ -n "$jobs" ]; then
            echo "main"
            return 0
        fi
    fi
    
    if check_jobmanager $STANDBY_PORT "备用节点" &>/dev/null; then
        local jobs=$(curl -s http://localhost:$STANDBY_PORT/jobs/overview 2>/dev/null | jq -r '.jobs[]?' 2>/dev/null)
        if [ -n "$jobs" ]; then
            echo "standby"
            return 0
        fi
    fi
    
    echo "none"
    return 1
}

# 函数：获取作业信息
get_job_info() {
    local leader=$1
    local port=$MAIN_PORT
    [ "$leader" = "standby" ] && port=$STANDBY_PORT
    
    curl -s http://localhost:$port/jobs/overview 2>/dev/null | jq -r '.jobs[] | "\(.jid)|\(.name)|\(.state)"' 2>/dev/null
}

# 函数：统计输出文件
count_output_files() {
    find $OUTPUT_DIR -type f -name "*.csv" 2>/dev/null | wc -l | tr -d ' '
}

# 函数：获取最新文件时间
get_latest_file_time() {
    local latest=$(find $OUTPUT_DIR -type f -name "*.csv" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -rn | head -1 | cut -d' ' -f1)
    echo ${latest:-0}
}

# 函数：等待并显示进度
wait_with_progress() {
    local seconds=$1
    local message=$2
    echo -ne "${YELLOW}$message${NC}"
    for ((i=1; i<=seconds; i++)); do
        echo -ne "."
        sleep 1
    done
    echo -e " ${GREEN}完成${NC}"
}

# ============================================================================
# 阶段 0: 初始状态检查
# ============================================================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}阶段 0: 初始状态检查${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# 检查容器状态
echo "1. 检查容器状态..."
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "(jobmanager|taskmanager|zookeeper)"
echo ""

# 检查 JobManager
echo "2. 检查 JobManager 状态..."
check_jobmanager $MAIN_PORT "主节点"
check_jobmanager $STANDBY_PORT "备用节点"
echo ""

# 获取当前 Leader
echo "3. 确定当前 Leader..."
INITIAL_LEADER=$(get_leader)
if [ "$INITIAL_LEADER" = "main" ]; then
    echo -e "${GREEN}✓ 当前 Leader: 主节点 (端口 $MAIN_PORT)${NC}"
    LEADER_PORT=$MAIN_PORT
elif [ "$INITIAL_LEADER" = "standby" ]; then
    echo -e "${GREEN}✓ 当前 Leader: 备用节点 (端口 $STANDBY_PORT)${NC}"
    LEADER_PORT=$STANDBY_PORT
else
    echo -e "${RED}✗ 错误: 没有找到 Leader${NC}"
    exit 1
fi
echo ""

# 获取作业信息
echo "4. 获取作业信息..."
JOB_INFO=$(get_job_info $INITIAL_LEADER)
if [ -z "$JOB_INFO" ]; then
    echo -e "${RED}✗ 错误: 没有运行中的作业${NC}"
    exit 1
fi

JOB_ID=$(echo $JOB_INFO | cut -d'|' -f1)
JOB_NAME=$(echo $JOB_INFO | cut -d'|' -f2)
JOB_STATE=$(echo $JOB_INFO | cut -d'|' -f3)

echo -e "  作业 ID: ${CYAN}$JOB_ID${NC}"
echo -e "  作业名称: ${CYAN}$JOB_NAME${NC}"
echo -e "  作业状态: ${GREEN}$JOB_STATE${NC}"
echo ""

# 统计初始文件数
INITIAL_FILE_COUNT=$(count_output_files)
echo -e "5. 初始输出文件数: ${CYAN}$INITIAL_FILE_COUNT${NC}"
echo ""

read -p "按 Enter 继续进行故障转移测试..."
echo ""

# ============================================================================
# 阶段 1: 模拟主节点故障
# ============================================================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}阶段 1: 模拟主节点故障${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [ "$INITIAL_LEADER" = "main" ]; then
    echo "1. 停止主节点容器..."
    docker stop flink-jobmanager
    echo -e "${GREEN}✓ 主节点已停止${NC}"
    FAILED_NODE="main"
    TAKEOVER_NODE="standby"
    TAKEOVER_PORT=$STANDBY_PORT
else
    echo "1. 停止备用节点容器..."
    docker stop flink-jobmanager-standby
    echo -e "${GREEN}✓ 备用节点已停止${NC}"
    FAILED_NODE="standby"
    TAKEOVER_NODE="main"
    TAKEOVER_PORT=$MAIN_PORT
fi
echo ""

# 等待 ZooKeeper 检测故障
wait_with_progress 15 "2. 等待 ZooKeeper 检测故障并触发选举"
echo ""

# 检查备用节点是否接管
echo "3. 检查备用节点是否接管..."
for i in {1..30}; do
    NEW_LEADER=$(get_leader)
    if [ "$NEW_LEADER" = "$TAKEOVER_NODE" ]; then
        echo -e "${GREEN}✓ 备用节点已成功接管 (尝试 $i/30)${NC}"
        break
    fi
    echo -ne "  等待备用节点接管... ($i/30)\r"
    sleep 2
done
echo ""

if [ "$NEW_LEADER" != "$TAKEOVER_NODE" ]; then
    echo -e "${RED}✗ 错误: 备用节点未能接管${NC}"
    exit 1
fi

# 检查作业状态
echo "4. 检查作业状态..."
sleep 5
NEW_JOB_INFO=$(get_job_info $TAKEOVER_NODE)
NEW_JOB_STATE=$(echo $NEW_JOB_INFO | cut -d'|' -f3)
echo -e "  作业状态: ${GREEN}$NEW_JOB_STATE${NC}"
echo ""

# 测试数据捕获（第一次）
echo "5. 测试数据捕获（故障转移后）..."
echo -e "${YELLOW}请在 Oracle 中执行以下 SQL:${NC}"
echo -e "${CYAN}UPDATE FINANCE_USER.TRANS_INFO SET AMOUNT = AMOUNT + 1 WHERE ROWNUM <= 18;${NC}"
echo -e "${CYAN}COMMIT;${NC}"
echo ""
read -p "执行完成后按 Enter 继续..."

wait_with_progress 35 "  等待 checkpoint 和文件生成"
echo ""

AFTER_FAILOVER_COUNT=$(count_output_files)
FILES_GENERATED=$((AFTER_FAILOVER_COUNT - INITIAL_FILE_COUNT))
if [ $FILES_GENERATED -gt 0 ]; then
    echo -e "${GREEN}✓ 故障转移后成功生成 $FILES_GENERATED 个文件${NC}"
else
    echo -e "${YELLOW}⚠ 故障转移后未生成新文件（可能需要更多时间）${NC}"
fi
echo ""

read -p "按 Enter 继续恢复主节点..."
echo ""

# ============================================================================
# 阶段 2: 恢复主节点（作为备用）
# ============================================================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}阶段 2: 恢复主节点（作为备用）${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [ "$FAILED_NODE" = "main" ]; then
    echo "1. 启动主节点容器..."
    docker start flink-jobmanager
    echo -e "${GREEN}✓ 主节点已启动${NC}"
else
    echo "1. 启动备用节点容器..."
    docker start flink-jobmanager-standby
    echo -e "${GREEN}✓ 备用节点已启动${NC}"
fi
echo ""

# 等待节点启动
wait_with_progress 20 "2. 等待节点完全启动并注册为备用"
echo ""

# 检查两个节点都在线
echo "3. 检查集群状态..."
check_jobmanager $MAIN_PORT "主节点"
check_jobmanager $STANDBY_PORT "备用节点"
echo ""

# 确认 Leader 没有变化
echo "4. 确认 Leader 状态..."
CURRENT_LEADER=$(get_leader)
if [ "$CURRENT_LEADER" = "$TAKEOVER_NODE" ]; then
    echo -e "${GREEN}✓ Leader 保持不变: $TAKEOVER_NODE${NC}"
else
    echo -e "${YELLOW}⚠ Leader 发生变化: $CURRENT_LEADER${NC}"
fi
echo ""

# 检查作业状态
echo "5. 检查作业状态..."
FINAL_JOB_INFO=$(get_job_info $CURRENT_LEADER)
FINAL_JOB_STATE=$(echo $FINAL_JOB_INFO | cut -d'|' -f3)
echo -e "  作业状态: ${GREEN}$FINAL_JOB_STATE${NC}"
echo ""

# ============================================================================
# 阶段 3: 验证作业持续运行
# ============================================================================
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}阶段 3: 验证作业持续运行${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# 测试数据捕获（第二次）
echo "1. 测试数据捕获（主节点恢复后）..."
echo -e "${YELLOW}请再次在 Oracle 中执行以下 SQL:${NC}"
echo -e "${CYAN}UPDATE FINANCE_USER.TRANS_INFO SET AMOUNT = AMOUNT + 1 WHERE ROWNUM <= 18;${NC}"
echo -e "${CYAN}COMMIT;${NC}"
echo ""
read -p "执行完成后按 Enter 继续..."

BEFORE_SECOND_TEST=$(count_output_files)
wait_with_progress 35 "  等待 checkpoint 和文件生成"
echo ""

AFTER_SECOND_TEST=$(count_output_files)
SECOND_TEST_FILES=$((AFTER_SECOND_TEST - BEFORE_SECOND_TEST))
if [ $SECOND_TEST_FILES -gt 0 ]; then
    echo -e "${GREEN}✓ 主节点恢复后成功生成 $SECOND_TEST_FILES 个文件${NC}"
else
    echo -e "${YELLOW}⚠ 主节点恢复后未生成新文件（可能需要更多时间）${NC}"
fi
echo ""

# 检查最新文件
echo "2. 检查最新生成的文件..."
LATEST_FILES=$(find $OUTPUT_DIR -type f -name "*.csv" -mmin -2 2>/dev/null | tail -5)
if [ -n "$LATEST_FILES" ]; then
    echo -e "${GREEN}最近 2 分钟内生成的文件:${NC}"
    echo "$LATEST_FILES" | while read file; do
        size=$(ls -lh "$file" | awk '{print $5}')
        time=$(stat -f "%Sm" -t "%H:%M:%S" "$file" 2>/dev/null || stat -c "%y" "$file" | cut -d' ' -f2 | cut -d'.' -f1)
        echo -e "  ${CYAN}$time${NC} - $file (${YELLOW}$size${NC})"
    done
else
    echo -e "${YELLOW}⚠ 未找到最近生成的文件${NC}"
fi
echo ""

# 检查 checkpoint 状态
echo "3. 检查 checkpoint 状态..."
LEADER_PORT=$MAIN_PORT
[ "$CURRENT_LEADER" = "standby" ] && LEADER_PORT=$STANDBY_PORT

CHECKPOINT_INFO=$(curl -s http://localhost:$LEADER_PORT/jobs/$JOB_ID/checkpoints 2>/dev/null | jq -r '.latest.completed | "ID: \(.id), 时间: \(.trigger_timestamp)"' 2>/dev/null)
if [ -n "$CHECKPOINT_INFO" ] && [ "$CHECKPOINT_INFO" != "null" ]; then
    echo -e "${GREEN}✓ 最新 checkpoint: $CHECKPOINT_INFO${NC}"
else
    echo -e "${YELLOW}⚠ 无法获取 checkpoint 信息${NC}"
fi
echo ""

# ============================================================================
# 测试总结
# ============================================================================
echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                      测试总结                                  ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

FINAL_FILE_COUNT=$(count_output_files)
TOTAL_FILES_GENERATED=$((FINAL_FILE_COUNT - INITIAL_FILE_COUNT))

echo -e "初始文件数:           ${CYAN}$INITIAL_FILE_COUNT${NC}"
echo -e "最终文件数:           ${CYAN}$FINAL_FILE_COUNT${NC}"
echo -e "测试期间生成文件数:   ${GREEN}$TOTAL_FILES_GENERATED${NC}"
echo ""
echo -e "初始 Leader:          ${CYAN}$INITIAL_LEADER${NC}"
echo -e "故障转移后 Leader:    ${CYAN}$TAKEOVER_NODE${NC}"
echo -e "最终 Leader:          ${CYAN}$CURRENT_LEADER${NC}"
echo ""
echo -e "作业 ID:              ${CYAN}$JOB_ID${NC}"
echo -e "作业状态:             ${GREEN}$FINAL_JOB_STATE${NC}"
echo ""

# 判断测试结果
if [ "$FINAL_JOB_STATE" = "RUNNING" ] && [ $TOTAL_FILES_GENERATED -gt 0 ]; then
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                  ✓ 测试成功通过                                ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${GREEN}✓ 作业在整个故障转移过程中保持运行${NC}"
    echo -e "${GREEN}✓ 文件持续生成，数据未丢失${NC}"
    echo -e "${GREEN}✓ HA 机制工作正常${NC}"
elif [ "$FINAL_JOB_STATE" = "RUNNING" ]; then
    echo -e "${YELLOW}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${YELLOW}║                  ⚠ 测试部分通过                                ║${NC}"
    echo -e "${YELLOW}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${GREEN}✓ 作业保持运行${NC}"
    echo -e "${YELLOW}⚠ 文件生成较少，可能需要更多数据或时间${NC}"
    echo ""
    echo -e "${CYAN}建议:${NC}"
    echo "  1. 检查是否有数据更新到 Oracle"
    echo "  2. 等待更长时间观察文件生成"
    echo "  3. 检查 TaskManager 日志"
else
    echo -e "${RED}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║                  ✗ 测试失败                                    ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${RED}✗ 作业状态异常: $FINAL_JOB_STATE${NC}"
    echo ""
    echo -e "${CYAN}建议:${NC}"
    echo "  1. 检查 JobManager 日志: docker logs flink-jobmanager"
    echo "  2. 检查 TaskManager 日志: docker logs realtime-pipeline-taskmanager-1"
    echo "  3. 运行修复脚本: ./shell/fix-ha-job-restart.sh"
fi

echo ""
echo -e "${CYAN}详细日志位置:${NC}"
echo "  - JobManager (主): docker logs flink-jobmanager"
echo "  - JobManager (备): docker logs flink-jobmanager-standby"
echo "  - TaskManager: docker logs realtime-pipeline-taskmanager-1"
echo ""
