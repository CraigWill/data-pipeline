#!/bin/bash

echo "=== 检查并启用 Oracle 归档日志模式 ==="
echo ""

# 1. 检查当前归档日志状态
echo "1. 检查当前归档日志状态..."
sqlplus64 -S system/helowin@localhost:1521/helowin <<EOF
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT log_mode FROM v\$database;
EXIT;
EOF

CURRENT_MODE=$(sqlplus64 -S system/helowin@localhost:1521/helowin <<EOF
SET PAGESIZE 0 FEEDBACK OFF VERIFY OFF HEADING OFF ECHO OFF
SELECT log_mode FROM v\$database;
EXIT;
EOF | tr -d ' \n\r')

echo "当前模式: $CURRENT_MODE"
echo ""

if [ "$CURRENT_MODE" = "ARCHIVELOG" ]; then
    echo "✅ 归档日志模式已经启用"
    echo ""
    
    # 显示归档日志配置
    echo "归档日志配置:"
    sqlplus64 -S system/helowin@localhost:1521/helowin <<EOF
SET PAGESIZE 100 FEEDBACK ON VERIFY OFF HEADING ON ECHO OFF
SELECT dest_name, status, destination FROM v\$archive_dest WHERE status != 'INACTIVE';
ARCHIVE LOG LIST;
EXIT;
EOF
    
else
    echo "❌ 归档日志模式未启用，正在启用..."
    echo ""
    
    # 启用归档日志
    sqlplus64 -S system/helowin@localhost:1521/helowin as sysdba <<EOF
-- 关闭数据库
SHUTDOWN IMMEDIATE;

-- 启动到 MOUNT 状态
STARTUP MOUNT;

-- 启用归档日志
ALTER DATABASE ARCHIVELOG;

-- 打开数据库
ALTER DATABASE OPEN;

-- 验证
SELECT 'Archive log mode: ' || log_mode FROM v\$database;

-- 显示归档日志配置
ARCHIVE LOG LIST;

EXIT;
EOF

    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ 归档日志模式已成功启用"
    else
        echo ""
        echo "❌ 启用归档日志模式失败"
        exit 1
    fi
fi

echo ""
echo "=== 完成 ==="
