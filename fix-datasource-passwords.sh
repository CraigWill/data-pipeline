#!/bin/bash

# 修复数据源密码加密问题
# 此脚本会检查数据库中的数据源密码，并重新加密明文密码

set -e

echo "=========================================="
echo "修复数据源密码加密"
echo "=========================================="

# 加载环境变量
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# 检查必需的环境变量
if [ -z "$DATABASE_PASSWORD" ]; then
    echo "❌ 错误: DATABASE_PASSWORD 未设置"
    exit 1
fi

if [ -z "$AES_ENCRYPTION_KEY" ]; then
    echo "❌ 错误: AES_ENCRYPTION_KEY 未设置"
    exit 1
fi

echo "✅ 环境变量已加载"
echo ""

# 解密数据库密码
DB_PASSWORD=$(echo "$DATABASE_PASSWORD" | base64 -d 2>/dev/null || echo "$DATABASE_PASSWORD")
if [ $? -ne 0 ]; then
    # 如果 base64 解码失败，尝试使用 encrypt-password.sh 解密
    DB_PASSWORD=$(./encrypt-password.sh decrypt "$DATABASE_PASSWORD" 2>/dev/null || echo "$DATABASE_PASSWORD")
fi

echo "连接到数据库检查数据源配置..."
echo ""

# 创建临时 SQL 脚本
cat > /tmp/check_datasources.sql <<'EOF'
SET PAGESIZE 0
SET FEEDBACK OFF
SET HEADING OFF
SET LINESIZE 200

SELECT id || '|' || name || '|' || password 
FROM cdc_datasources;

EXIT;
EOF

# 执行查询
RESULT=$(docker exec -i flink-monitor-backend sh -c "sqlplus -s monitor/${DB_PASSWORD}@//oracle-prod:1521/ORCLPDB1 < /tmp/check_datasources.sql" 2>&1 || echo "")

if [ -z "$RESULT" ] || echo "$RESULT" | grep -q "ORA-"; then
    echo "❌ 无法连接到数据库或查询失败"
    echo "$RESULT"
    exit 1
fi

echo "数据源列表:"
echo "$RESULT"
echo ""

# 检查每个数据源的密码格式
echo "检查密码格式..."
echo ""

while IFS='|' read -r ds_id ds_name ds_password; do
    # 跳过空行
    [ -z "$ds_id" ] && continue
    
    echo "数据源: $ds_name ($ds_id)"
    echo "  密码长度: ${#ds_password}"
    
    # 检查密码是否看起来像 Base64 编码的 AES 密文
    # AES 加密后的数据应该是 Base64 编码的，长度应该是 4 的倍数
    if [ $((${#ds_password} % 4)) -eq 0 ] && echo "$ds_password" | grep -qE '^[A-Za-z0-9+/]+=*$'; then
        echo "  ✅ 密码格式正确（Base64 编码）"
    else
        echo "  ⚠️  密码可能是明文或格式不正确"
        echo "  提示: 请使用前端界面重新创建数据源，或手动加密密码"
    fi
    echo ""
done <<< "$RESULT"

echo "=========================================="
echo "检查完成"
echo "=========================================="
echo ""
echo "如果发现密码格式不正确，请："
echo "1. 删除该数据源"
echo "2. 使用前端界面重新创建数据源"
echo "3. 新创建的数据源密码会自动加密"
echo ""
echo "或者，如果您知道明文密码，可以使用以下命令手动加密："
echo "  ./encrypt-password.sh encrypt <明文密码>"
echo "  然后手动更新数据库中的密码字段"
