#!/bin/bash
# Vue 3 前端快速部署脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================="
echo "Flink CDC 监控系统 - Vue 3 前端部署"
echo "==========================================${NC}"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: 未安装 Docker${NC}"
    exit 1
fi

# 检查 Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}错误: 未安装 Docker Compose${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker 版本: $(docker --version)${NC}"
echo -e "${GREEN}✓ Docker Compose 版本: $(docker-compose --version)${NC}"
echo ""

# 选择部署模式
echo "请选择部署模式："
echo "  1) 完整部署（所有服务）"
echo "  2) 仅部署前端"
echo "  3) 重新构建前端"
echo "  4) 查看服务状态"
echo "  5) 查看前端日志"
echo "  6) 停止所有服务"
echo ""
read -p "请输入选项 (1-6): " choice

case $choice in
    1)
        echo ""
        echo -e "${YELLOW}开始完整部署...${NC}"
        echo ""
        
        # 构建前端
        echo "步骤 1/3: 构建 Vue 3 前端..."
        docker-compose build monitor-frontend
        
        # 启动所有服务
        echo ""
        echo "步骤 2/3: 启动所有服务..."
        docker-compose up -d
        
        # 等待服务启动
        echo ""
        echo "步骤 3/3: 等待服务启动..."
        sleep 10
        
        # 显示状态
        echo ""
        docker-compose ps
        
        echo ""
        echo -e "${GREEN}=========================================="
        echo "✓ 部署完成！"
        echo "==========================================${NC}"
        echo ""
        echo "访问地址："
        echo "  Vue 3 前端: http://localhost:8888"
        echo "  后端 API:   http://localhost:5001"
        echo "  Flink UI:   http://localhost:8081"
        echo ""
        echo "查看日志: docker-compose logs -f monitor-frontend"
        ;;
        
    2)
        echo ""
        echo -e "${YELLOW}仅部署前端...${NC}"
        echo ""
        
        # 构建并启动前端
        docker-compose build monitor-frontend
        docker-compose up -d monitor-frontend
        
        # 等待启动
        sleep 5
        
        # 显示状态
        docker-compose ps monitor-frontend
        
        echo ""
        echo -e "${GREEN}✓ 前端部署完成！${NC}"
        echo "访问地址: http://localhost:8888"
        ;;
        
    3)
        echo ""
        echo -e "${YELLOW}重新构建前端...${NC}"
        echo ""
        
        # 停止前端
        docker-compose stop monitor-frontend
        
        # 删除旧容器和镜像
        docker-compose rm -f monitor-frontend
        docker rmi -f $(docker images -q flink-monitor-vue) 2>/dev/null || true
        
        # 重新构建
        docker-compose build --no-cache monitor-frontend
        
        # 启动
        docker-compose up -d monitor-frontend
        
        # 等待启动
        sleep 5
        
        echo ""
        echo -e "${GREEN}✓ 重新构建完成！${NC}"
        echo "访问地址: http://localhost:8888"
        ;;
        
    4)
        echo ""
        echo "服务状态："
        echo ""
        docker-compose ps
        
        echo ""
        echo "前端健康检查："
        curl -s http://localhost:8888/health || echo -e "${RED}前端未运行${NC}"
        
        echo ""
        echo "后端健康检查："
        curl -s http://localhost:5001/api/health || echo -e "${RED}后端未运行${NC}"
        ;;
        
    5)
        echo ""
        echo "前端日志（按 Ctrl+C 退出）："
        echo ""
        docker-compose logs -f monitor-frontend
        ;;
        
    6)
        echo ""
        echo -e "${YELLOW}停止所有服务...${NC}"
        docker-compose down
        echo ""
        echo -e "${GREEN}✓ 所有服务已停止${NC}"
        ;;
        
    *)
        echo -e "${RED}无效选项${NC}"
        exit 1
        ;;
esac

echo ""
