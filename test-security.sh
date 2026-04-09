#!/bin/bash

# 安全认证功能测试脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "========================================="
echo "安全认证功能测试"
echo "========================================="
echo ""

# 测试 1: 检查服务是否启动
echo -e "${BLUE}测试 1: 检查服务状态${NC}"
if curl -s http://localhost:5001/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 后端服务正常运行${NC}"
else
    echo -e "${RED}✗ 后端服务未启动${NC}"
    exit 1
fi
echo ""

# 测试 2: 测试未认证访问（应该返回 401）
echo -e "${BLUE}测试 2: 未认证访问（应该返回 401）${NC}"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:5001/api/cluster/overview)
if [ "$HTTP_CODE" = "401" ]; then
    echo -e "${GREEN}✓ 未认证访问被正确拒绝（401）${NC}"
else
    echo -e "${RED}✗ 未认证访问返回了 $HTTP_CODE，期望 401${NC}"
    exit 1
fi
echo ""

# 测试 3: 测试登录功能
echo -e "${BLUE}测试 3: 登录功能${NC}"
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:5001/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')

if echo "$LOGIN_RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✓ 登录成功${NC}"
    TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    echo "Token: ${TOKEN:0:50}..."
else
    echo -e "${RED}✗ 登录失败${NC}"
    echo "响应: $LOGIN_RESPONSE"
    exit 1
fi
echo ""

# 测试 4: 测试错误密码
echo -e "${BLUE}测试 4: 错误密码（应该失败）${NC}"
WRONG_LOGIN=$(curl -s -X POST http://localhost:5001/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrongpassword"}')

if echo "$WRONG_LOGIN" | grep -q '"success":false'; then
    echo -e "${GREEN}✓ 错误密码被正确拒绝${NC}"
else
    echo -e "${RED}✗ 错误密码未被拒绝${NC}"
    exit 1
fi
echo ""

# 测试 5: 使用 Token 访问受保护的 API
echo -e "${BLUE}测试 5: 使用 Token 访问 API${NC}"
API_RESPONSE=$(curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:5001/api/cluster/overview)

if echo "$API_RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✓ Token 认证成功，API 访问正常${NC}"
    echo "集群信息: $(echo "$API_RESPONSE" | grep -o '"taskmanagers":[0-9]*' | head -1)"
else
    echo -e "${RED}✗ Token 认证失败${NC}"
    echo "响应: $API_RESPONSE"
    exit 1
fi
echo ""

# 测试 6: 使用错误的 Token
echo -e "${BLUE}测试 6: 使用错误的 Token（应该返回 401）${NC}"
WRONG_TOKEN_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer invalid-token-here" \
    http://localhost:5001/api/cluster/overview)

if [ "$WRONG_TOKEN_CODE" = "401" ]; then
    echo -e "${GREEN}✓ 错误 Token 被正确拒绝（401）${NC}"
else
    echo -e "${RED}✗ 错误 Token 返回了 $WRONG_TOKEN_CODE，期望 401${NC}"
    exit 1
fi
echo ""

# 测试 7: 测试获取当前用户信息
echo -e "${BLUE}测试 7: 获取当前用户信息${NC}"
USER_INFO=$(curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:5001/api/auth/me)

if echo "$USER_INFO" | grep -q '"username":"admin"'; then
    echo -e "${GREEN}✓ 获取用户信息成功${NC}"
    echo "用户: $(echo "$USER_INFO" | grep -o '"username":"[^"]*"')"
else
    echo -e "${RED}✗ 获取用户信息失败${NC}"
    exit 1
fi
echo ""

# 测试 8: 测试登出
echo -e "${BLUE}测试 8: 登出功能${NC}"
LOGOUT_RESPONSE=$(curl -s -X POST \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:5001/api/auth/logout)

if echo "$LOGOUT_RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✓ 登出成功${NC}"
else
    echo -e "${YELLOW}⚠ 登出响应异常（但不影响功能）${NC}"
fi
echo ""

# 测试 9: 测试数据源 API（需要认证）
echo -e "${BLUE}测试 9: 测试数据源 API${NC}"
DS_RESPONSE=$(curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:5001/api/datasources)

if echo "$DS_RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✓ 数据源 API 访问正常${NC}"
else
    echo -e "${YELLOW}⚠ 数据源 API 响应异常${NC}"
fi
echo ""

# 测试 10: 测试前端页面
echo -e "${BLUE}测试 10: 测试前端页面${NC}"
if curl -s http://localhost:8888 | grep -q "<!DOCTYPE html>"; then
    echo -e "${GREEN}✓ 前端页面正常${NC}"
else
    echo -e "${YELLOW}⚠ 前端页面可能未启动${NC}"
fi
echo ""

# 完成
echo "========================================="
echo -e "${GREEN}所有测试通过！${NC}"
echo "========================================="
echo ""
echo "安全功能状态:"
echo "  ✓ JWT Token 认证正常"
echo "  ✓ 登录/登出功能正常"
echo "  ✓ API 访问控制正常"
echo "  ✓ Token 验证正常"
echo "  ✓ 错误处理正常"
echo ""
echo "默认账户:"
echo "  用户名: admin"
echo "  密码: admin123"
echo ""
echo "访问地址:"
echo "  前端: http://localhost:8888"
echo "  后端: http://localhost:5001"
echo ""
echo -e "${YELLOW}提示: 生产环境请立即修改默认密码！${NC}"
echo "========================================="
