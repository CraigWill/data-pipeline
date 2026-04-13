#!/bin/bash
# 在 Oracle 容器中执行 SQL 脚本的辅助脚本

set -e

SQL_FILE=$1
USERNAME=$2
PASSWORD=$3
CONNECT_STRING=$4

if [ -z "$SQL_FILE" ] || [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; then
    echo "用法: $0 <sql_file> <username> <password> [connect_string]"
    echo "示例: $0 script.sql system helowin helowin"
    exit 1
fi

# 默认连接字符串
if [ -z "$CONNECT_STRING" ]; then
    CONNECT_STRING="helowin"
fi

# 检查 SQL 文件是否存在
if [ ! -f "$SQL_FILE" ]; then
    echo "错误: SQL 文件不存在: $SQL_FILE"
    exit 1
fi

echo "=========================================="
echo "执行 SQL 脚本: $SQL_FILE"
echo "用户: $USERNAME"
echo "连接: $CONNECT_STRING"
echo "=========================================="
echo ""

# 复制 SQL 文件到容器
docker cp "$SQL_FILE" oracle11g:/tmp/script.sql

# 在容器中执行 SQL
docker exec oracle11g bash -c "
    source /home/oracle/.bash_profile
    export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
    export PATH=\$ORACLE_HOME/bin:\$PATH
    export LD_LIBRARY_PATH=\$ORACLE_HOME/lib:\$LD_LIBRARY_PATH
    
    sqlplus -S $USERNAME/$PASSWORD@$CONNECT_STRING @/tmp/script.sql
"

EXIT_CODE=$?

# 清理临时文件
docker exec oracle11g rm -f /tmp/script.sql

if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "✅ SQL 脚本执行成功"
    echo "=========================================="
else
    echo ""
    echo "=========================================="
    echo "❌ SQL 脚本执行失败 (退出码: $EXIT_CODE)"
    echo "=========================================="
fi

exit $EXIT_CODE
