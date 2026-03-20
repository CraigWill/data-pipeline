#!/bin/bash
# 部署测试脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=========================================="
echo "部署测试脚本"
echo "==========================================${NC}"
echo ""

# 测试计数
PASSED=0
FAILED=0

# 测试函数
test_service() {
    local name=$1
    local url=$2
    local expected=$3
    
    echo -n "测试 $name... "
    
    if curl -s -f "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 通过${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ 失败${NC}"
        ((FAILED++))
    fi
}

# 测试 Docker 服务
echo "1. 检查 Docker 服务"
echo "-------------------"
if docker ps > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Docker 运行正常${NC}"
    ((PASSED++))
else
    echo -e "${RED}✗ Docker 未运行${NC}"
    ((FAILED++))
fi
echo ""

# 测试容器状态
echo "2. 检查容器状态"
echo "-------------------"
containers=("flink-monitor-frontend" "flink-monitor-backend" "flink-jobmanager")
for container in "${containers[@]}"; do
    echo -n "检查 $container... "
    if docker ps --filter "name=$container" --filter "status=running" | grep -q "$container"; then
        echo -e "${GREEN}✓ 运行中${NC}"
        ((PASSED++))
    else
        echo -e "${YELLOW}⚠ 未运行${NC}"
        ((FAILED++))
    fi
done
echo ""

# 测试服务端点
echo "3. 检查服务端点"
echo "-------------------"
test_service "Vue 3 前端" "http://localhost:8888/health"
test_service "后端 API" "http://localhost:5001/api/health"
test_service "Flink UI" "http://localhost:8081/overview"
echo ""

# 测试前端页面
echo "4. 检查前端页面"
echo "-------------------"
pages=("/" "/tasks" "/datasources" "/jobs" "/cluster")
for page in "${pages[@]}"; do
    echo -n "测试页面 $page... "
    if curl -s -f "http://localhost:8888$page" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 可访问${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ 无法访问${NC}"
        ((FAILED++))
    fi
done
echo ""

# 测试 API 端点
echo "5. 检查 API 端点"
echo "-------------------"
apis=("/api/cluster/overview" "/api/jobs" "/api/datasources" "/api/cdc/tasks")
for api in "${apis[@]}"; do
    echo -n "测试 API $api... "
    status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:5001$api")
    if [ "$status" = "200" ] || [ "$status" = "404" ]; then
        echo -e "${GREEN}✓ 响应正常 ($status)${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ 响应异常 ($status)${NC}"
        ((FAILED++))
    fi
done
echo ""

# 测试健康检查
echo "6. 检查健康状态"
echo "-------------------"
echo -n "前端健康检查... "
health=$(curl -s http://localhost:8888/health)
if [ "$health" = "healthy" ]; then
    echo -e "${GREEN}✓ 健康${NC}"
    ((PASSED++))
else
    echo -e "${RED}✗ 不健康${NC}"
    ((FAILED++))
fi
echo ""

# 显示总结
echo -e "${BLUE}=========================================="
echo "测试总结"
echo "==========================================${NC}"
echo ""
echo -e "通过: ${GREEN}$PASSED${NC}"
echo -e "失败: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ 所有测试通过！部署成功！${NC}"
    echo ""
    echo "访问地址："
    echo "  Vue 3 前端: http://localhost:8888"
    echo "  后端 API:   http://localhost:5001"
    echo "  Flink UI:   http://localhost:8081"
    exit 0
else
    echo -e "${YELLOW}⚠ 部分测试失败，请检查日志${NC}"
    echo ""
    echo "查看日志："
    echo "  docker-compose logs -f monitor-frontend"
    echo "  docker-compose logs -f monitor-backend"
    exit 1
fi
