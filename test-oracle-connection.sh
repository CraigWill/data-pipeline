#!/bin/bash
# 简单的 Oracle 数据库连接测试

echo "=========================================="
echo "Oracle 数据库连接测试"
echo "=========================================="
echo ""

# 加载环境变量
if [ -f .env ]; then
    source <(grep -v '^#' .env | grep -v '^$' | sed 's/^/export /')
fi

DB_HOST=${DATABASE_HOST:-localhost}
DB_PORT=${DATABASE_PORT:-1521}
DB_USER=${DATABASE_USERNAME:-finance_user}
DB_SCHEMA=${DATABASE_SCHEMA:-helowin}
DB_TABLE=${DATABASE_TABLES:-trans_info}

echo "配置信息:"
echo "  主机: $DB_HOST"
echo "  端口: $DB_PORT"
echo "  用户: $DB_USER"
echo "  Schema: $DB_SCHEMA"
echo "  表: $DB_TABLE"
echo ""

# 测试端口连接
echo "测试 1: 检查端口连接"
echo "----------------------------------------"
if command -v nc &> /dev/null; then
    if timeout 5 nc -z $DB_HOST $DB_PORT 2>/dev/null; then
        echo "✅ 端口 $DB_HOST:$DB_PORT 可访问"
        PORT_OK=true
    else
        echo "❌ 端口 $DB_HOST:$DB_PORT 无法访问"
        PORT_OK=false
    fi
elif command -v telnet &> /dev/null; then
    if timeout 5 bash -c "echo quit | telnet $DB_HOST $DB_PORT" 2>/dev/null | grep -q "Connected"; then
        echo "✅ 端口 $DB_HOST:$DB_PORT 可访问"
        PORT_OK=true
    else
        echo "❌ 端口 $DB_HOST:$DB_PORT 无法访问"
        PORT_OK=false
    fi
else
    echo "⚠️  nc 或 telnet 命令不可用，跳过端口测试"
    PORT_OK=unknown
fi
echo ""

# 测试 Oracle 客户端
echo "测试 2: 检查 Oracle 客户端"
echo "----------------------------------------"
if command -v sqlplus &> /dev/null; then
    echo "✅ sqlplus 已安装"
    SQLPLUS_OK=true
    
    # 尝试连接
    echo ""
    echo "尝试使用 sqlplus 连接..."
    echo "连接字符串: $DB_USER@$DB_HOST:$DB_PORT/$DB_SCHEMA"
    echo ""
    
    # 创建临时 SQL 脚本
    cat > /tmp/test_oracle.sql << EOF
SET PAGESIZE 0
SET FEEDBACK OFF
SET HEADING OFF
SELECT 'Connection successful!' FROM DUAL;
SELECT COUNT(*) || ' records in $DB_SCHEMA.$DB_TABLE' FROM $DB_SCHEMA.$DB_TABLE;
EXIT;
EOF
    
    # 执行测试（需要密码）
    echo "提示: 需要输入数据库密码"
    sqlplus -S $DB_USER@$DB_HOST:$DB_PORT/$DB_SCHEMA @/tmp/test_oracle.sql
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ 数据库连接成功！"
        DB_OK=true
    else
        echo ""
        echo "❌ 数据库连接失败"
        DB_OK=false
    fi
    
    rm -f /tmp/test_oracle.sql
else
    echo "❌ sqlplus 未安装"
    SQLPLUS_OK=false
    DB_OK=unknown
fi
echo ""

# 检查 Docker 中的 Oracle
echo "测试 3: 检查本地 Oracle 容器"
echo "----------------------------------------"
if command -v docker &> /dev/null; then
    ORACLE_CONTAINERS=$(docker ps --filter "ancestor=*oracle*" --format "{{.Names}}" 2>/dev/null)
    if [ -n "$ORACLE_CONTAINERS" ]; then
        echo "✅ 发现 Oracle 容器:"
        echo "$ORACLE_CONTAINERS" | while read container; do
            echo "   - $container"
        done
    else
        echo "⚠️  未发现运行中的 Oracle 容器"
        
        # 检查所有容器
        ALL_DB_CONTAINERS=$(docker ps --format "{{.Names}}: {{.Image}}" | grep -i "oracle\|database" 2>/dev/null)
        if [ -n "$ALL_DB_CONTAINERS" ]; then
            echo ""
            echo "发现其他数据库容器:"
            echo "$ALL_DB_CONTAINERS"
        fi
    fi
else
    echo "⚠️  Docker 未安装或不可用"
fi
echo ""

# 总结
echo "=========================================="
echo "测试总结"
echo "=========================================="
echo ""

if [ "$PORT_OK" = "true" ]; then
    echo "✅ 网络连接: 正常"
elif [ "$PORT_OK" = "false" ]; then
    echo "❌ 网络连接: 失败"
    echo ""
    echo "可能的原因:"
    echo "  1. Oracle 数据库未启动"
    echo "  2. 主机地址或端口配置错误"
    echo "  3. 防火墙阻止连接"
    echo ""
    echo "解决方案:"
    echo "  - 检查 .env 文件中的 DATABASE_HOST 和 DATABASE_PORT"
    echo "  - 确认 Oracle 数据库正在运行"
    echo "  - 如果使用 Docker: docker ps | grep oracle"
else
    echo "⚠️  网络连接: 未测试"
fi

if [ "$DB_OK" = "true" ]; then
    echo "✅ 数据库连接: 成功"
    echo ""
    echo "🎉 可以启动 CDC 应用程序了！"
    echo ""
    echo "运行命令:"
    echo "  ./start-cdc.sh"
elif [ "$DB_OK" = "false" ]; then
    echo "❌ 数据库连接: 失败"
    echo ""
    echo "可能的原因:"
    echo "  1. 用户名或密码错误"
    echo "  2. Schema 不存在"
    echo "  3. 表不存在或无权限"
    echo ""
    echo "解决方案:"
    echo "  - 检查 .env 文件中的数据库凭据"
    echo "  - 验证 Schema 和表名是否正确"
    echo "  - 确认用户有访问权限"
else
    echo "⚠️  数据库连接: 未测试（需要 sqlplus）"
    echo ""
    echo "CDC 应用程序说明:"
    echo "  - 如果无法连接数据库，应用会自动切换到模拟模式"
    echo "  - 模拟模式会生成测试数据用于演示"
    echo ""
    echo "可以直接运行:"
    echo "  ./start-cdc.sh"
    echo ""
    echo "安装 Oracle 客户端（可选）:"
    echo "  macOS: brew install instantclient-sqlplus"
    echo "  Linux: 下载 Oracle Instant Client"
fi

echo ""
echo "=========================================="
