#!/bin/bash
# 验证环境变量配置

echo "=========================================="
echo "验证环境变量配置"
echo "=========================================="
echo ""

# 必需的变量
REQUIRED_VARS=(
    "JWT_SECRET"
    "AES_ENCRYPTION_KEY"
    "DATABASE_PASSWORD"
)

# 可选的变量
OPTIONAL_VARS=(
    "DATABASE_HOST"
    "DATABASE_PORT"
    "DATABASE_USERNAME"
    "DATABASE_SID"
)

# 检查必需变量
echo "必需的环境变量:"
all_required_set=true
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        echo "  ❌ $var: 未设置"
        all_required_set=false
    else
        # 只显示前 20 个字符
        value="${!var}"
        echo "  ✓ $var: ${value:0:20}..."
    fi
done

echo ""
echo "可选的环境变量:"
for var in "${OPTIONAL_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        echo "  ⚠ $var: 未设置（将使用默认值）"
    else
        echo "  ✓ $var: ${!var}"
    fi
done

echo ""
echo "=========================================="
if [ "$all_required_set" = true ]; then
    echo "✓ 所有必需的环境变量都已正确设置"
    echo "=========================================="
    exit 0
else
    echo "❌ 部分必需的环境变量未设置"
    echo "=========================================="
    echo ""
    echo "请运行以下命令加载环境变量:"
    echo "  source ~/.zshrc"
    echo ""
    exit 1
fi
