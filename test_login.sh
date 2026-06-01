#!/bin/bash

# 测试 K8s 环境中的登录流程

echo "=== 测试登录流程 ==="

# 1. 获取验证码
echo "1. 获取验证码..."
CAPTCHA_RESPONSE=$(curl -s "http://localhost:5001/api/auth/captcha" -H "Content-Type: application/json")
echo "验证码响应: $CAPTCHA_RESPONSE"

# 提取 captchaId
CAPTCHA_ID=$(echo $CAPTCHA_RESPONSE | grep -o '"captchaId":"[^"]*"' | cut -d'"' -f4)
echo "验证码ID: $CAPTCHA_ID"

# 2. 尝试登录（使用默认验证码 "1234"）
echo "2. 尝试登录..."
LOGIN_RESPONSE=$(curl -s -X POST "http://localhost:5001/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"Admin2026Secure\",\"captchaId\":\"$CAPTCHA_ID\",\"captchaCode\":\"1234\"}")

echo "登录响应: $LOGIN_RESPONSE"

# 3. 检查响应
if echo "$LOGIN_RESPONSE" | grep -q "success.*true"; then
    echo "✅ 登录成功!"
    # 提取 token
    TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    echo "Token: $TOKEN"
    
    # 4. 测试受保护的端点
    echo "3. 测试受保护端点..."
    PROTECTED_RESPONSE=$(curl -s "http://localhost:5001/api/datasources" \
      -H "Authorization: Bearer $TOKEN")
    echo "受保护端点响应: $PROTECTED_RESPONSE"
else
    echo "❌ 登录失败"
    # 尝试其他验证码
    echo "尝试常见验证码..."
    for CODE in "1234" "1111" "0000" "8888" "9999"; do
        echo "尝试验证码: $CODE"
        RESPONSE=$(curl -s -X POST "http://localhost:5001/api/auth/login" \
          -H "Content-Type: application/json" \
          -d "{\"username\":\"admin\",\"password\":\"Admin2026Secure\",\"captchaId\":\"$CAPTCHA_ID\",\"captchaCode\":\"$CODE\"}")
        if echo "$RESPONSE" | grep -q "success.*true"; then
            echo "✅ 使用验证码 $CODE 登录成功!"
            TOKEN=$(echo $RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
            echo "Token: $TOKEN"
            break
        fi
    done
fi