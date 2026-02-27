#!/bin/bash
# 检查 Flink 高可用（HA）状态脚本

echo "=========================================="
echo "Flink 高可用（HA）状态检查"
echo "时间: $(date)"
echo "=========================================="

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查函数
check_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ $2${NC}"
        return 0
    else
        echo -e "${RED}❌ $2${NC}"
        return 1
    fi
}

echo ""
echo "1. 容器状态检查"
echo "----------------------------------------"

# 检查 Zookeeper
if docker ps | grep -q zookeeper; then
    check_status 0 "Zookeeper 容器运行中"
    ZK_STATUS=$(docker exec zookeeper zkServer.sh status 2>&1 | grep "Mode:" || echo "unknown")
    echo "   状态: $ZK_STATUS"
else
    check_status 1 "Zookeeper 容器未运行"
fi

# 检查 JobManager
if docker ps | grep -q flink-jobmanager; then
    check_status 0 "JobManager 容器运行中"
else
    check_status 1 "JobManager 容器未运行"
fi

# 检查 TaskManager
TM_COUNT=$(docker ps | grep -c "taskmanager" || echo "0")
if [ "$TM_COUNT" -gt 0 ]; then
    check_status 0 "TaskManager 容器运行中 (数量: $TM_COUNT)"
else
    check_status 1 "TaskManager 容器未运行"
fi

echo ""
echo "2. Zookeeper 集成检查"
echo "----------------------------------------"

# 检查 Flink 在 Zookeeper 中的注册
if docker exec zookeeper zkCli.sh ls /flink 2>/dev/null | grep -q "realtime-pipeline"; then
    check_status 0 "Flink 集群已在 Zookeeper 注册"
    
    echo ""
    echo "   Zookeeper 节点结构:"
    docker exec zookeeper zkCli.sh ls /flink/realtime-pipeline 2>/dev/null | sed 's/^/   /' || true
    
    # 检查 Leader 选举
    if docker exec zookeeper zkCli.sh ls /flink/realtime-pipeline 2>/dev/null | grep -q "leader"; then
        check_status 0 "Leader 选举节点存在"
    else
        check_status 1 "Leader 选举节点不存在"
    fi
else
    check_status 1 "Flink 集群未在 Zookeeper 注册"
    echo -e "${YELLOW}   提示: 可能 HA 模式未启用或 JobManager 未启动${NC}"
fi

echo ""
echo "3. Flink Web UI 检查"
echo "----------------------------------------"

if curl -s http://localhost:8081/overview >/dev/null 2>&1; then
    check_status 0 "Flink Web UI 可访问"
    
    # 获取集群详细信息
    OVERVIEW=$(curl -s http://localhost:8081/overview)
    
    TM_COUNT=$(echo "$OVERVIEW" | grep -o '"taskmanagers":[0-9]*' | grep -o '[0-9]*')
    SLOTS_TOTAL=$(echo "$OVERVIEW" | grep -o '"slots-total":[0-9]*' | grep -o '[0-9]*')
    SLOTS_AVAILABLE=$(echo "$OVERVIEW" | grep -o '"slots-available":[0-9]*' | grep -o '[0-9]*')
    JOBS_RUNNING=$(echo "$OVERVIEW" | grep -o '"jobs-running":[0-9]*' | grep -o '[0-9]*')
    
    echo "   TaskManager 数量: ${TM_COUNT:-0}"
    echo "   总槽位数: ${SLOTS_TOTAL:-0}"
    echo "   可用槽位数: ${SLOTS_AVAILABLE:-0}"
    echo "   运行中的作业: ${JOBS_RUNNING:-0}"
else
    check_status 1 "Flink Web UI 不可访问"
fi

echo ""
echo "4. HA 配置检查"
echo "----------------------------------------"

# 检查环境变量
if [ -f .env ]; then
    HA_MODE=$(grep "^HA_MODE=" .env | cut -d'=' -f2)
    HA_ZK_QUORUM=$(grep "^HA_ZOOKEEPER_QUORUM=" .env | cut -d'=' -f2)
    
    if [ "$HA_MODE" = "zookeeper" ]; then
        check_status 0 "HA 模式已启用 (模式: $HA_MODE)"
        echo "   Zookeeper 地址: ${HA_ZK_QUORUM:-未配置}"
    else
        check_status 1 "HA 模式未启用 (当前: ${HA_MODE:-NONE})"
    fi
else
    check_status 1 ".env 文件不存在"
fi

# 检查 JobManager 配置
if docker exec flink-jobmanager cat /opt/flink/conf/flink-conf.yaml 2>/dev/null | grep -q "high-availability: zookeeper"; then
    check_status 0 "JobManager HA 配置已启用"
else
    check_status 1 "JobManager HA 配置未启用"
fi

echo ""
echo "5. HA 存储检查"
echo "----------------------------------------"

# 检查 HA 存储目录
if docker exec flink-jobmanager ls /opt/flink/ha >/dev/null 2>&1; then
    HA_FILES=$(docker exec flink-jobmanager ls -la /opt/flink/ha 2>/dev/null | wc -l)
    check_status 0 "HA 存储目录存在"
    echo "   文件数量: $((HA_FILES - 3))"  # 减去 ., .., total 行
else
    check_status 1 "HA 存储目录不存在"
fi

# 检查 Checkpoint 目录
if docker exec flink-jobmanager ls /opt/flink/checkpoints >/dev/null 2>&1; then
    check_status 0 "Checkpoint 目录存在"
else
    check_status 1 "Checkpoint 目录不存在"
fi

echo ""
echo "6. 日志分析"
echo "----------------------------------------"

# 检查 HA 相关日志
echo "最近的 HA 相关日志:"
docker-compose logs --tail=50 jobmanager 2>/dev/null | grep -i "high-availability\|zookeeper\|leader" | tail -5 | sed 's/^/   /' || echo "   未找到 HA 相关日志"

echo ""
echo "=========================================="
echo "状态检查完成"
echo "=========================================="

# 总结
echo ""
echo "快速访问:"
echo "  - Flink Web UI: http://localhost:8081"
echo "  - Zookeeper 节点: docker exec zookeeper zkCli.sh ls /flink/realtime-pipeline"
echo "  - JobManager 日志: docker-compose logs -f jobmanager"
echo "  - TaskManager 日志: docker-compose logs -f taskmanager"
echo ""
echo "常用命令:"
echo "  - 重启 JobManager: docker-compose restart jobmanager"
echo "  - 扩展 TaskManager: docker-compose up -d --scale taskmanager=3"
echo "  - 查看所有容器: docker-compose ps"
echo ""
