#!/bin/bash
# 重新构建所有镜像脚本

set -e

echo "=== 重新构建所有 Docker 镜像 ==="
echo ""

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 1. 检查 JAR 文件
echo -e "${YELLOW}>>> 检查 JAR 文件...${NC}"
if ! ls flink-jobs/target/flink-jobs-*.jar 1> /dev/null 2>&1; then
    echo -e "${RED}✗ Flink Jobs JAR 不存在${NC}"
    echo "请先运行: ./quick-build.sh"
    exit 1
fi

if ! ls monitor-backend/target/monitor-backend-*.jar 1> /dev/null 2>&1; then
    echo -e "${RED}✗ Monitor Backend JAR 不存在${NC}"
    echo "请先运行: ./quick-build.sh"
    exit 1
fi

echo -e "${GREEN}✓ JAR 文件检查通过${NC}"
echo ""

# 2. 构建 JobManager 镜像
echo -e "${YELLOW}>>> 构建 JobManager 镜像...${NC}"
docker build -f docker/jobmanager/Dockerfile.cn -t flink-jobmanager:latest .
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ JobManager 镜像构建成功${NC}"
else
    echo -e "${RED}✗ JobManager 镜像构建失败${NC}"
    exit 1
fi
echo ""

# 3. 构建 TaskManager 镜像
echo -e "${YELLOW}>>> 构建 TaskManager 镜像...${NC}"
docker build -f docker/taskmanager/Dockerfile.cn -t flink-taskmanager:latest .
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ TaskManager 镜像构建成功${NC}"
else
    echo -e "${RED}✗ TaskManager 镜像构建失败${NC}"
    exit 1
fi
echo ""

# 4. 显示镜像信息
echo -e "${GREEN}=== 镜像构建完成 ===${NC}"
docker images | grep -E "REPOSITORY|flink-(jobmanager|taskmanager)"
echo ""

echo -e "${GREEN}=== 下一步 ===${NC}"
echo "1. 启动服务: docker-compose up -d"
echo "2. 查看状态: ./start.sh --status"
echo "3. 访问 Flink UI: http://localhost:8081"
