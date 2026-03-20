#!/bin/bash
# Vue 3 前端构建脚本

set -e

echo "=========================================="
echo "Vue 3 前端构建脚本"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo -e "${RED}错误: 未安装 Node.js${NC}"
    echo "请先安装 Node.js 18 或更高版本"
    exit 1
fi

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 16 ]; then
    echo -e "${RED}错误: Node.js 版本过低 (当前: $(node -v))${NC}"
    echo "请升级到 Node.js 16 或更高版本"
    exit 1
fi

echo -e "${GREEN}✓ Node.js 版本: $(node -v)${NC}"

# 检查 npm
if ! command -v npm &> /dev/null; then
    echo -e "${RED}错误: 未安装 npm${NC}"
    exit 1
fi

echo -e "${GREEN}✓ npm 版本: $(npm -v)${NC}"

# 进入前端目录
cd "$(dirname "$0")"

# 安装依赖
echo ""
echo "=========================================="
echo "安装依赖..."
echo "=========================================="
npm install

# 构建
echo ""
echo "=========================================="
echo "构建生产版本..."
echo "=========================================="
npm run build

# 检查构建产物
if [ ! -d "dist" ]; then
    echo -e "${RED}错误: 构建失败，未找到 dist 目录${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}=========================================="
echo "✓ 构建成功！"
echo "==========================================${NC}"
echo ""
echo "构建产物位置: $(pwd)/dist"
echo ""
echo "下一步操作："
echo "  1. 本地预览: npm run preview"
echo "  2. Docker 构建: docker build -t flink-monitor-vue ."
echo "  3. Docker Compose: docker-compose up -d monitor-frontend"
echo ""
