#!/bin/bash
# Flink HA 故障转移测试脚本
# 测试 JobManager 故障转移功能

set -e

echo "=========================================="
echo "Flink HA 故障转移测试"
echo "时间: $(date)"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 步骤 1: 检查当前 Leader
echo "步骤 1: 检查当前 Leader"
CURRENT_LEADER=""
if curl -s http://localhost:8081/overview >/dev/null 2>&1; then
    CURRENT_LEADER="jobmanager"
    echo -e "   ${GREEN}当前 Leader: JobManager 主节点 (端口 8081)${NC}"
elif curl -s http://localhost:8082/overview >/dev/null 2>&1; then
    CURRENT_LEADER="jobmanager-standby"
    echo -e "   ${YELLOW}当前 Leader: JobManager 备节点 (端口 8082)${NC}"
else
    echo -e "   ${RED}错误: 无法连接到任何 JobManager${NC}"
    exit 1
fi
echo ""

# 步骤 2: 记录当前作业
echo "步骤 2: 记录当前作业"
LEADER_PORT=$([ "$CURRENT_LEADER" = "jobmanager" ] && echo "8081" || echo "8082")
JOB_ID=$(curl -s http://localhost:$LEADER_PORT/jobs | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    jobs = data.get('jobs', [])
    if jobs:
        print(jobs[0]['id'])
except:
    pass
" 2>/dev/null)

if [ -n "$JOB_ID" ]; then
    echo "   作业 ID: $JOB_ID"
    JOB_STATUS=$(curl -s http://localhost:$LEADER_PORT/jobs/$JOB_ID | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('state', 'UNKNOWN'))
except:
    print('UNKNOWN')
" 2>/dev/null)
    echo "   作业状态: $JOB_STATUS"
else
    echo "   无运行作业"
fi
echo ""

# 步骤 3: 停止当前 Leader
echo "步骤 3: 停止当前 Leader"
echo -e "   ${YELLOW}警告: 即将停止 $CURRENT_LEADER${NC}"
read -p "   确认继续？(y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "   测试已取消"
    exit 0
fi

echo "   停止 $CURRENT_LEADER..."
docker-compose stop $CURRENT_LEADER
echo -e "   ${GREEN}$CURRENT_LEADER 已停止${NC}"
echo ""

# 步骤 4: 等待故障转移
echo "步骤 4: 等待故障转移（30秒）"
for i in {30..1}; do
    echo -ne "   倒计时: $i 秒\r"
    sleep 1
done
echo ""
echo ""

# 步骤 5: 检查新的 Leader
echo "步骤 5: 检查新的 Leader"
NEW_LEADER=""
NEW_LEADER_PORT=""
if curl -s http://localhost:8081/overview >/dev/null 2>&1; then
    NEW_LEADER="jobmanager"
    NEW_LEADER_PORT="8081"
    echo -e "   ${GREEN}新 Leader: JobManager 主节点 (端口 8081)${NC}"
elif curl -s http://localhost:8082/overview >/dev/null 2>&1; then
    NEW_LEADER="jobmanager-standby"
    NEW_LEADER_PORT="8082"
    echo -e "   ${GREEN}新 Leader: JobManager 备节点 (端口 8082)${NC}"
else
    echo -e "   ${RED}错误: 故障转移失败，无法连接到任何 JobManager${NC}"
    echo ""
    echo "恢复原 Leader:"
    docker-compose start $CURRENT_LEADER
    exit 1
fi

if [ "$NEW_LEADER" = "$CURRENT_LEADER" ]; then
    echo -e "   ${RED}错误: Leader 未切换${NC}"
    exit 1
fi
echo ""

# 步骤 6: 验证作业状态
echo "步骤 6: 验证作业状态"
if [ -n "$JOB_ID" ]; then
    sleep 5
    NEW_JOB_STATUS=$(curl -s http://localhost:$NEW_LEADER_PORT/jobs/$JOB_ID | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('state', 'UNKNOWN'))
except:
    print('UNKNOWN')
" 2>/dev/null)
    
    echo "   作业 ID: $JOB_ID"
    echo "   作业状态: $NEW_JOB_STATUS"
    
    if [ "$NEW_JOB_STATUS" = "RUNNING" ]; then
        echo -e "   ${GREEN}作业继续运行${NC}"
    else
        echo -e "   ${YELLOW}作业状态: $NEW_JOB_STATUS${NC}"
    fi
else
    echo "   无作业需要验证"
fi
echo ""

# 步骤 7: 检查 CSV 文件生成
echo "步骤 7: 检查 CSV 文件生成"
if [ -d "output/cdc" ]; then
    RECENT_FILES=$(find output/cdc -name "*.csv" -type f -mmin -2 2>/dev/null | wc -l)
    if [ "$RECENT_FILES" -gt 0 ]; then
        echo -e "   ${GREEN}最近 2 分钟内生成了 $RECENT_FILES 个 CSV 文件${NC}"
        find output/cdc -name "*.csv" -type f -mmin -2 2>/dev/null | head -3 | while read file; do
            SIZE=$(ls -lh "$file" | awk '{print $5}')
            echo "     - $file ($SIZE)"
        done
    else
        echo -e "   ${YELLOW}最近 2 分钟内无新 CSV 文件${NC}"
    fi
else
    echo "   输出目录不存在"
fi
echo ""

# 步骤 8: 显示测试结果
echo "=========================================="
echo "故障转移测试结果"
echo "=========================================="
echo ""
echo "原 Leader:     $CURRENT_LEADER (端口 $LEADER_PORT)"
echo "新 Leader:     $NEW_LEADER (端口 $NEW_LEADER_PORT)"
echo "故障转移:     成功 ✓"
if [ -n "$JOB_ID" ]; then
    echo "作业状态:     $NEW_JOB_STATUS"
fi
echo ""
echo "下一步操作:"
echo "  1. 查看新 Leader Web UI: http://localhost:$NEW_LEADER_PORT"
echo "  2. 查看日志: docker-compose logs -f $NEW_LEADER"
echo "  3. 恢复原 Leader: docker-compose start $CURRENT_LEADER"
echo "  4. 监控集群: ./shell/monitor-ha-cluster.sh"
echo ""
echo "恢复原 Leader:"
read -p "是否现在恢复原 Leader？(y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "恢复 $CURRENT_LEADER..."
    docker-compose start $CURRENT_LEADER
    echo "等待 30 秒..."
    sleep 30
    echo -e "${GREEN}$CURRENT_LEADER 已恢复（现在是 Standby）${NC}"
    echo ""
    docker-compose ps | grep jobmanager
fi
echo ""
echo "=========================================="
echo "测试完成！"
echo "=========================================="
