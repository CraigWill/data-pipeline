#!/bin/bash
# 健康检查测试脚本
# 用于验证容器健康检查配置是否正常工作

set -e

echo "=========================================="
echo "容器健康检查测试"
echo "时间: $(date)"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}错误: Docker未运行${NC}"
    exit 1
fi

# 检查docker-compose是否安装
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}错误: docker-compose未安装${NC}"
    exit 1
fi

echo ""
echo "1. 检查容器状态"
echo "----------------------------------------"

# 获取所有容器状态
CONTAINERS=("flink-jobmanager" "flink-taskmanager-1" "cdc-collector")

for container in "${CONTAINERS[@]}"; do
    if docker ps -a --format "{{.Names}}" | grep -q "^${container}$"; then
        STATUS=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "unknown")
        HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "none")
        
        echo -n "  $container: "
        
        if [ "$STATUS" = "running" ]; then
            echo -n -e "${GREEN}运行中${NC}"
        else
            echo -n -e "${RED}$STATUS${NC}"
        fi
        
        if [ "$HEALTH" != "none" ]; then
            if [ "$HEALTH" = "healthy" ]; then
                echo -e " - ${GREEN}健康${NC}"
            elif [ "$HEALTH" = "unhealthy" ]; then
                echo -e " - ${RED}不健康${NC}"
            elif [ "$HEALTH" = "starting" ]; then
                echo -e " - ${YELLOW}启动中${NC}"
            else
                echo -e " - ${YELLOW}$HEALTH${NC}"
            fi
        else
            echo " - 无健康检查"
        fi
    else
        echo -e "  $container: ${RED}不存在${NC}"
    fi
done

echo ""
echo "2. 测试健康检查端点"
echo "----------------------------------------"

# 测试JobManager
echo -n "  JobManager Web UI: "
if curl -f -s http://localhost:8081/overview > /dev/null 2>&1; then
    echo -e "${GREEN}可访问${NC}"
else
    echo -e "${RED}不可访问${NC}"
fi

# 测试CDC Collector健康检查端点
echo -n "  CDC Collector /health: "
if curl -f -s http://localhost:8080/health > /dev/null 2>&1; then
    echo -e "${GREEN}可访问${NC}"
else
    echo -e "${RED}不可访问${NC}"
fi

echo -n "  CDC Collector /health/live: "
if curl -f -s http://localhost:8080/health/live > /dev/null 2>&1; then
    echo -e "${GREEN}可访问${NC}"
else
    echo -e "${RED}不可访问${NC}"
fi

echo -n "  CDC Collector /health/ready: "
if curl -f -s http://localhost:8080/health/ready > /dev/null 2>&1; then
    echo -e "${GREEN}可访问${NC}"
else
    echo -e "${RED}不可访问${NC}"
fi

echo ""
echo "3. 查看健康检查详情"
echo "----------------------------------------"

for container in "${CONTAINERS[@]}"; do
    if docker ps --format "{{.Names}}" | grep -q "^${container}$"; then
        echo "  $container:"
        
        # 获取健康检查日志
        HEALTH_LOG=$(docker inspect --format='{{json .State.Health}}' "$container" 2>/dev/null)
        
        if [ "$HEALTH_LOG" != "null" ] && [ -n "$HEALTH_LOG" ]; then
            # 提取关键信息
            STATUS=$(echo "$HEALTH_LOG" | jq -r '.Status // "unknown"')
            FAILING_STREAK=$(echo "$HEALTH_LOG" | jq -r '.FailingStreak // 0')
            
            echo "    状态: $STATUS"
            echo "    连续失败次数: $FAILING_STREAK"
            
            # 显示最近的健康检查日志
            echo "    最近检查:"
            echo "$HEALTH_LOG" | jq -r '.Log[-3:] | .[] | "      [\(.Start)] 退出码: \(.ExitCode)"' 2>/dev/null || echo "      无日志"
        else
            echo "    无健康检查配置"
        fi
        echo ""
    fi
done

echo "4. 测试自动重启（可选）"
echo "----------------------------------------"
echo "  要测试自动重启功能，请运行:"
echo "    docker exec flink-jobmanager pkill -9 java"
echo "    docker ps -a  # 观察容器状态"
echo "    docker logs -f flink-jobmanager  # 查看重启日志"
echo ""

echo "5. 查看容器重启次数"
echo "----------------------------------------"

for container in "${CONTAINERS[@]}"; do
    if docker ps -a --format "{{.Names}}" | grep -q "^${container}$"; then
        RESTART_COUNT=$(docker inspect --format='{{.RestartCount}}' "$container" 2>/dev/null || echo "0")
        echo "  $container: $RESTART_COUNT 次重启"
    fi
done

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
