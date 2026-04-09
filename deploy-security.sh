#!/bin/bash

# 安全认证功能部署脚本
# 用途：快速部署带有安全认证的监控系统

set -e

echo "========================================="
echo "安全认证功能部署脚本"
echo "========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 .env 文件
if [ ! -f .env ]; then
    echo -e "${YELLOW}警告: .env 文件不存在，从 .env.example 复制${NC}"
    cp .env.example .env
    echo -e "${GREEN}✓ 已创建 .env 文件${NC}"
    echo ""
fi

# 检查是否配置了安全密钥
if grep -q "your-256-bit-secret-key-change-this-in-production" .env 2>/dev/null; then
    echo -e "${RED}警告: 检测到默认的 JWT_SECRET！${NC}"
    echo -e "${YELLOW}生产环境必须修改 JWT_SECRET 和 AES_ENCRYPTION_KEY${NC}"
    echo ""
    
    read -p "是否自动生成新的密钥？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "生成新密钥..."
        
        # 生成 JWT 密钥
        JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
        
        # 生成 AES 密钥
        AES_KEY=$(openssl rand -base64 32 | tr -d '\n')
        
        # 更新 .env 文件
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            sed -i '' "s|JWT_SECRET=.*|JWT_SECRET=$JWT_SECRET|g" .env
            sed -i '' "s|AES_ENCRYPTION_KEY=.*|AES_ENCRYPTION_KEY=$AES_KEY|g" .env
        else
            # Linux
            sed -i "s|JWT_SECRET=.*|JWT_SECRET=$JWT_SECRET|g" .env
            sed -i "s|AES_ENCRYPTION_KEY=.*|AES_ENCRYPTION_KEY=$AES_KEY|g" .env
        fi
        
        echo -e "${GREEN}✓ 已生成并保存新密钥到 .env 文件${NC}"
        echo ""
    fi
fi

# 步骤 1: 编译后端
echo "步骤 1/5: 编译后端..."
cd monitor-backend
mvn clean package -DskipTests
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 后端编译成功${NC}"
else
    echo -e "${RED}✗ 后端编译失败${NC}"
    exit 1
fi
cd ..
echo ""

# 步骤 2: 编译前端
echo "步骤 2/5: 编译前端..."
cd monitor/frontend-vue
if [ ! -d "node_modules" ]; then
    echo "安装前端依赖..."
    npm install
fi
npm run build
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 前端编译成功${NC}"
else
    echo -e "${RED}✗ 前端编译失败${NC}"
    exit 1
fi
cd ../..
echo ""

# 步骤 3: 构建 Docker 镜像
echo "步骤 3/5: 构建 Docker 镜像..."
docker-compose build --no-cache monitor-backend monitor-frontend
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Docker 镜像构建成功${NC}"
else
    echo -e "${RED}✗ Docker 镜像构建失败${NC}"
    exit 1
fi
echo ""

# 步骤 4: 停止并删除旧容器
echo "步骤 4/5: 停止并删除旧容器..."
docker-compose stop monitor-backend monitor-frontend
docker-compose rm -f monitor-backend monitor-frontend
echo -e "${GREEN}✓ 旧容器已删除${NC}"
echo ""

# 步骤 5: 启动新容器
echo "步骤 5/5: 启动新容器..."
docker-compose up -d monitor-backend monitor-frontend
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 服务重启成功${NC}"
else
    echo -e "${RED}✗ 服务重启失败${NC}"
    exit 1
fi
echo ""

# 步骤 6: 等待服务启动
echo "步骤 6/6: 等待服务启动..."
echo "等待后端服务..."
for i in {1..30}; do
    if curl -s http://localhost:5001/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 后端服务已启动${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${YELLOW}警告: 后端服务启动超时，请检查日志${NC}"
    fi
    sleep 2
done

echo "等待前端服务..."
for i in {1..15}; do
    if curl -s http://localhost:8888 > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 前端服务已启动${NC}"
        break
    fi
    if [ $i -eq 15 ]; then
        echo -e "${YELLOW}警告: 前端服务启动超时，请检查日志${NC}"
    fi
    sleep 2
done
echo ""

# 测试登录
echo "测试登录功能..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:5001/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')

if echo "$LOGIN_RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✓ 登录功能正常${NC}"
    TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    
    # 测试受保护的 API
    echo "测试受保护的 API..."
    API_RESPONSE=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:5001/api/cluster/overview)
    if echo "$API_RESPONSE" | grep -q '"success":true'; then
        echo -e "${GREEN}✓ API 访问正常${NC}"
    else
        echo -e "${YELLOW}警告: API 访问异常${NC}"
    fi
else
    echo -e "${YELLOW}警告: 登录功能异常，请检查日志${NC}"
fi
echo ""

# 完成
echo "========================================="
echo -e "${GREEN}部署完成！${NC}"
echo "========================================="
echo ""
echo "访问地址:"
echo "  前端: http://localhost:8888"
echo "  后端: http://localhost:5001"
echo ""
echo "默认账户:"
echo "  用户名: admin"
echo "  密码: admin123"
echo ""
echo -e "${RED}重要提示:${NC}"
echo "  1. 生产环境请立即修改默认密码"
echo "  2. 确保 JWT_SECRET 和 AES_ENCRYPTION_KEY 已修改"
echo "  3. 启用 HTTPS"
echo "  4. 配置防火墙规则"
echo ""
echo "查看日志:"
echo "  docker-compose logs -f monitor-backend"
echo "  docker-compose logs -f monitor-frontend"
echo ""
echo "详细文档: SECURITY-DEPLOYMENT-GUIDE.md"
echo "========================================="
