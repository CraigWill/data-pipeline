#!/bin/bash
# 系统环境变量配置脚本
# 用于将 .env 文件中的关键环境变量添加到系统环境变量中

set -e

echo "=========================================="
echo "系统环境变量配置脚本"
echo "=========================================="
echo ""

# 检查 .env 文件是否存在
if [ ! -f .env ]; then
    echo "错误: .env 文件不存在"
    echo "请先运行: ./init-keys.sh"
    exit 1
fi

# 检测 shell 类型
SHELL_TYPE=$(basename "$SHELL")
echo "检测到 Shell: $SHELL_TYPE"
echo ""

# 确定配置文件
case "$SHELL_TYPE" in
    bash)
        PROFILE_FILE="$HOME/.bash_profile"
        RC_FILE="$HOME/.bashrc"
        ;;
    zsh)
        PROFILE_FILE="$HOME/.zprofile"
        RC_FILE="$HOME/.zshrc"
        ;;
    *)
        echo "警告: 未识别的 Shell 类型: $SHELL_TYPE"
        echo "将使用 ~/.profile"
        PROFILE_FILE="$HOME/.profile"
        RC_FILE="$HOME/.profile"
        ;;
esac

echo "配置文件: $RC_FILE"
echo ""

# 读取 .env 文件中的关键变量
echo "从 .env 文件读取环境变量..."

# 提取变量（处理多行值）
JWT_SECRET=$(grep "^JWT_SECRET=" .env | cut -d'=' -f2- | tr -d '\n' | sed 's/[[:space:]]*$//')
AES_ENCRYPTION_KEY=$(grep "^AES_ENCRYPTION_KEY=" .env | cut -d'=' -f2- | tr -d '\n' | sed 's/[[:space:]]*$//')
DATABASE_PASSWORD=$(grep "^DATABASE_PASSWORD=" .env | cut -d'=' -f2- | tr -d '\n' | sed 's/[[:space:]]*$//')
DATABASE_HOST=$(grep "^DATABASE_HOST=" .env | cut -d'=' -f2- | tr -d '\n' | sed 's/[[:space:]]*$//')
DATABASE_PORT=$(grep "^DATABASE_PORT=" .env | cut -d'=' -f2- | tr -d '\n' | sed 's/[[:space:]]*$//')
DATABASE_USERNAME=$(grep "^DATABASE_USERNAME=" .env | cut -d'=' -f2- | tr -d '\n' | sed 's/[[:space:]]*$//')
DATABASE_SID=$(grep "^DATABASE_SID=" .env | cut -d'=' -f2- | tr -d '\n' | sed 's/[[:space:]]*$//')

# 验证必需的变量
if [ -z "$JWT_SECRET" ]; then
    echo "错误: JWT_SECRET 未在 .env 文件中设置"
    exit 1
fi

if [ -z "$AES_ENCRYPTION_KEY" ]; then
    echo "错误: AES_ENCRYPTION_KEY 未在 .env 文件中设置"
    exit 1
fi

if [ -z "$DATABASE_PASSWORD" ]; then
    echo "错误: DATABASE_PASSWORD 未在 .env 文件中设置"
    exit 1
fi

echo "✓ JWT_SECRET: ${JWT_SECRET:0:20}..."
echo "✓ AES_ENCRYPTION_KEY: ${AES_ENCRYPTION_KEY:0:20}..."
echo "✓ DATABASE_PASSWORD: ${DATABASE_PASSWORD:0:10}..."
echo "✓ DATABASE_HOST: $DATABASE_HOST"
echo "✓ DATABASE_PORT: $DATABASE_PORT"
echo "✓ DATABASE_USERNAME: $DATABASE_USERNAME"
echo "✓ DATABASE_SID: $DATABASE_SID"
echo ""

# 备份配置文件
if [ -f "$RC_FILE" ]; then
    cp "$RC_FILE" "${RC_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
    echo "✓ 已备份配置文件: ${RC_FILE}.backup.*"
fi

# 创建环境变量配置块
ENV_BLOCK="
# ========================================
# 实时数据管道系统 - 环境变量
# 由 setup-env-vars.sh 自动生成
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')
# ========================================

# 安全密钥
export JWT_SECRET=\"$JWT_SECRET\"
export AES_ENCRYPTION_KEY=\"$AES_ENCRYPTION_KEY\"

# 数据库配置
export DATABASE_HOST=\"$DATABASE_HOST\"
export DATABASE_PORT=\"$DATABASE_PORT\"
export DATABASE_USERNAME=\"$DATABASE_USERNAME\"
export DATABASE_PASSWORD=\"$DATABASE_PASSWORD\"
export DATABASE_SID=\"$DATABASE_SID\"

# ========================================
"

# 检查是否已存在配置
if grep -q "# 实时数据管道系统 - 环境变量" "$RC_FILE" 2>/dev/null; then
    echo "检测到已存在的环境变量配置"
    read -p "是否要更新配置？(y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        # 删除旧的配置块
        sed -i.tmp '/# 实时数据管道系统 - 环境变量/,/# ========================================$/d' "$RC_FILE"
        rm -f "${RC_FILE}.tmp"
        echo "✓ 已删除旧配置"
    else
        echo "取消操作"
        exit 0
    fi
fi

# 添加新配置
echo "$ENV_BLOCK" >> "$RC_FILE"
echo "✓ 已添加环境变量到 $RC_FILE"
echo ""

# 限制文件权限
chmod 600 "$RC_FILE"
echo "✓ 已设置配置文件权限为 600"
echo ""

echo "=========================================="
echo "配置完成！"
echo "=========================================="
echo ""
echo "重要提示:"
echo "1. 环境变量已添加到 $RC_FILE"
echo "2. 需要重新加载配置文件或重启终端"
echo "3. 配置文件已备份"
echo ""
echo "立即生效（在当前终端）:"
echo "  source $RC_FILE"
echo ""
echo "验证环境变量:"
echo "  echo \$JWT_SECRET"
echo "  echo \$DATABASE_PASSWORD"
echo ""
echo "注意: 新打开的终端会自动加载这些环境变量"
echo ""
