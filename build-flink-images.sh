#!/bin/bash
# Flink 私有镜像构建脚本
# 用途：构建自定义的 Flink JobManager 和 TaskManager 镜像

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Flink 私有镜像构建脚本 ===${NC}"

# 检查 Maven 构建
if [ ! -f "flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar" ]; then
    echo -e "${YELLOW}未找到 JAR 文件，开始 Maven 构建...${NC}"
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo -e "${RED}Maven 构建失败${NC}"
        exit 1
    fi
    echo -e "${GREEN}Maven 构建成功${NC}"
else
    echo -e "${GREEN}找到 JAR 文件，跳过 Maven 构建${NC}"
fi

# 镜像标签
IMAGE_TAG=${1:-latest}
REGISTRY=${2:-""}

if [ -n "$REGISTRY" ]; then
    JOBMANAGER_IMAGE="${REGISTRY}/flink-jobmanager:${IMAGE_TAG}"
    TASKMANAGER_IMAGE="${REGISTRY}/flink-taskmanager:${IMAGE_TAG}"
else
    JOBMANAGER_IMAGE="flink-jobmanager:${IMAGE_TAG}"
    TASKMANAGER_IMAGE="flink-taskmanager:${IMAGE_TAG}"
fi

echo -e "${YELLOW}镜像标签: ${IMAGE_TAG}${NC}"
echo -e "${YELLOW}JobManager 镜像: ${JOBMANAGER_IMAGE}${NC}"
echo -e "${YELLOW}TaskManager 镜像: ${TASKMANAGER_IMAGE}${NC}"

# 构建 JobManager 镜像
echo -e "\n${GREEN}=== 构建 JobManager 镜像 ===${NC}"
docker build -f docker/jobmanager/Dockerfile -t ${JOBMANAGER_IMAGE} .
if [ $? -ne 0 ]; then
    echo -e "${RED}JobManager 镜像构建失败${NC}"
    exit 1
fi
echo -e "${GREEN}JobManager 镜像构建成功: ${JOBMANAGER_IMAGE}${NC}"

# 构建 TaskManager 镜像
echo -e "\n${GREEN}=== 构建 TaskManager 镜像 ===${NC}"
docker build -f docker/taskmanager/Dockerfile -t ${TASKMANAGER_IMAGE} .
if [ $? -ne 0 ]; then
    echo -e "${RED}TaskManager 镜像构建失败${NC}"
    exit 1
fi
echo -e "${GREEN}TaskManager 镜像构建成功: ${TASKMANAGER_IMAGE}${NC}"

# 显示镜像信息
echo -e "\n${GREEN}=== 镜像构建完成 ===${NC}"
docker images | grep -E "REPOSITORY|flink-(jobmanager|taskmanager)"

# 推送到镜像仓库（可选）
if [ -n "$REGISTRY" ]; then
    read -p "是否推送镜像到仓库 ${REGISTRY}? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}推送 JobManager 镜像...${NC}"
        docker push ${JOBMANAGER_IMAGE}
        echo -e "${YELLOW}推送 TaskManager 镜像...${NC}"
        docker push ${TASKMANAGER_IMAGE}
        echo -e "${GREEN}镜像推送完成${NC}"
    fi
fi

echo -e "\n${GREEN}=== 使用方法 ===${NC}"
echo "1. 本地使用："
echo "   docker-compose up -d"
echo ""
echo "2. 使用自定义标签："
echo "   ./build-flink-images.sh v1.0.0"
echo ""
echo "3. 推送到私有仓库："
echo "   ./build-flink-images.sh v1.0.0 registry.example.com/myproject"
