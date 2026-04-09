#!/bin/bash
# 密钥初始化脚本
# 用于生成强随机密钥并更新 .env 文件

set -e

echo "=========================================="
echo "密钥初始化脚本"
echo "=========================================="
echo ""

# 检查 .env 文件是否存在
if [ ! -f .env ]; then
    echo "错误: .env 文件不存在"
    echo "请先复制 .env.example 到 .env:"
    echo "  cp .env.example .env"
    exit 1
fi

# 检查是否已有密钥
if grep -q "^JWT_SECRET=<" .env && grep -q "^AES_ENCRYPTION_KEY=<" .env; then
    echo "检测到 .env 文件中的密钥为占位符，将生成新密钥..."
elif grep -q "^JWT_SECRET=" .env && grep -q "^AES_ENCRYPTION_KEY=" .env; then
    echo "警告: .env 文件中已存在密钥"
    read -p "是否要重新生成密钥？这将使所有已加密的密码失效！(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "取消操作"
        exit 0
    fi
fi

echo ""
echo "生成强随机密钥..."
echo ""

# 生成 JWT 密钥 (64 字节)
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
echo "✓ JWT 密钥已生成 (64 字节)"

# 生成 AES 密钥 (32 字节)
AES_KEY=$(openssl rand -base64 32 | tr -d '\n')
echo "✓ AES 密钥已生成 (32 字节)"

echo ""
echo "更新 .env 文件..."

# 备份原文件
cp .env .env.backup.$(date +%Y%m%d_%H%M%S)
echo "✓ 已备份原 .env 文件"

# 更新密钥
if grep -q "^JWT_SECRET=" .env; then
    # macOS 和 Linux 兼容的 sed 命令
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s|^JWT_SECRET=.*|JWT_SECRET=$JWT_SECRET|" .env
    else
        sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$JWT_SECRET|" .env
    fi
    echo "✓ JWT_SECRET 已更新"
else
    echo "JWT_SECRET=$JWT_SECRET" >> .env
    echo "✓ JWT_SECRET 已添加"
fi

if grep -q "^AES_ENCRYPTION_KEY=" .env; then
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s|^AES_ENCRYPTION_KEY=.*|AES_ENCRYPTION_KEY=$AES_KEY|" .env
    else
        sed -i "s|^AES_ENCRYPTION_KEY=.*|AES_ENCRYPTION_KEY=$AES_KEY|" .env
    fi
    echo "✓ AES_ENCRYPTION_KEY 已更新"
else
    echo "AES_ENCRYPTION_KEY=$AES_KEY" >> .env
    echo "✓ AES_ENCRYPTION_KEY 已添加"
fi

# 限制文件权限
chmod 600 .env
echo "✓ .env 文件权限已设置为 600"

echo ""
echo "=========================================="
echo "密钥初始化完成！"
echo "=========================================="
echo ""
echo "重要提示:"
echo "1. 密钥已保存到 .env 文件"
echo "2. 原文件已备份（.env.backup.*）"
echo "3. 如果之前有加密的密码，需要重新加密"
echo "4. 请重启所有服务以应用新密钥"
echo ""
echo "下一步:"
echo "  docker-compose restart"
echo ""
