#!/bin/bash
# Flink HA 集群监控脚本
# 实时监控 Flink 高可用集群的状态

echo "=== Flink HA 集群监控 ==="
echo "时间: $(date)"
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 1. 检查容器状态
echo "1. 容器状态:"
docker-compose ps --format "table {{.Name}}\t{{.Status}}" | grep -E "zookeeper|jobmanager|taskmanager" || echo "  无运行容器"
echo ""

# 2. 检查 Leader
echo "2. JobManager Leader:"
LEADER_PORT=""
if curl -s http://localhost:8081/overview >/dev/null 2>&1; then
    echo -e "   ${GREEN}Leader: JobManager 主节点 (端口 8081)${NC}"
    LEADER_PORT="8081"
elif curl -s http://localhost:8082/overview >/dev/null 2>&1; then
    echo -e "   ${YELLOW}Leader: JobManager 备节点 (端口 8082)${NC}"
    LEADER_PORT="8082"
else
    echo -e "   ${RED}错误: 无法连接到任何 JobManager${NC}"
    exit 1
fi
echo ""

# 3. 检查集群资源
echo "3. 集群资源:"
curl -s http://localhost:$LEADER_PORT/overview | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(f'   Flink 版本: {data.get(\"flink-version\", \"N/A\")}')
    print(f'   TaskManagers: {data.get(\"taskmanagers\", 0)}')
    print(f'   总槽位: {data.get(\"slots-total\", 0)}')
    print(f'   可用槽位: {data.get(\"slots-available\", 0)}')
    print(f'   运行作业: {data.get(\"jobs-running\", 0)}')
    print(f'   完成作业: {data.get(\"jobs-finished\", 0)}')
    print(f'   失败作业: {data.get(\"jobs-failed\", 0)}')
except Exception as e:
    print(f'   解析失败: {e}')
" 2>/dev/null || echo "   无法获取集群信息"
echo ""

# 4. 检查作业状态
echo "4. 作业状态:"
curl -s http://localhost:$LEADER_PORT/jobs | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    jobs = data.get('jobs', [])
    if not jobs:
        print('   无运行作业')
    else:
        for job in jobs:
            status = job.get('status', 'UNKNOWN')
            color = '\033[0;32m' if status == 'RUNNING' else '\033[0;31m'
            print(f'   作业 ID: {job.get(\"id\", \"N/A\")}')
            print(f'   状态: {color}{status}\033[0m')
except Exception as e:
    print(f'   解析失败: {e}')
" 2>/dev/null || echo "   无法获取作业信息"
echo ""

# 5. 检查最近的 CSV 文件
echo "5. 最近生成的 CSV 文件:"
if [ -d "output/cdc" ]; then
    find output/cdc -name "*.csv" -type f -mmin -5 2>/dev/null | head -3 | while read file; do
        SIZE=$(ls -lh "$file" | awk '{print $5}')
        MTIME=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" "$file" 2>/dev/null || stat -c "%y" "$file" 2>/dev/null | cut -d'.' -f1)
        echo "   $file ($SIZE, $MTIME)"
    done || echo "   最近 5 分钟内无新文件"
else
    echo "   输出目录不存在"
fi
echo ""

# 6. 检查 ZooKeeper 连接
echo "6. ZooKeeper 状态:"
if docker exec zookeeper zookeeper-shell localhost:2181 ls /flink/realtime-pipeline 2>/dev/null | grep -q "leader"; then
    echo -e "   ${GREEN}ZooKeeper 连接正常${NC}"
    echo "   HA 节点:"
    docker exec zookeeper zookeeper-shell localhost:2181 ls /flink/realtime-pipeline 2>/dev/null | grep -o '\[.*\]' | tr ',' '\n' | sed 's/\[//;s/\]//;s/^/     - /'
else
    echo -e "   ${RED}ZooKeeper 连接异常${NC}"
fi
echo ""

# 7. 检查 Checkpoint
echo "7. Checkpoint 状态:"
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
    curl -s http://localhost:$LEADER_PORT/jobs/$JOB_ID/checkpoints | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    latest = data.get('latest', {}).get('completed', {})
    if latest:
        print(f'   最新 Checkpoint ID: {latest.get(\"id\", \"N/A\")}')
        print(f'   完成时间: {latest.get(\"latest_ack_timestamp\", \"N/A\")}')
        print(f'   状态大小: {latest.get(\"state_size\", 0)} bytes')
    else:
        print('   无 Checkpoint 数据')
except Exception as e:
    print(f'   解析失败: {e}')
" 2>/dev/null || echo "   无法获取 Checkpoint 信息"
else
    echo "   无运行作业"
fi
echo ""

echo "=== 监控完成 ==="
echo ""
echo "提示:"
echo "  - 查看实时日志: docker-compose logs -f jobmanager"
echo "  - 访问 Web UI: http://localhost:$LEADER_PORT"
echo "  - 测试故障转移: docker-compose stop jobmanager"
echo "  - 查看详细文档: docs/FLINK_HA_DEPLOYMENT_GUIDE.md"
