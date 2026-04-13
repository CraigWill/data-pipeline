#!/bin/bash
# Oracle CDC 完整配置脚本
# 一键执行所有 SQL 配置

set -e

echo "=========================================="
echo "Oracle CDC 完整配置"
echo "=========================================="
echo ""

# 检查 Docker 容器是否运行
if ! docker ps | grep -q oracle11g; then
    echo "❌ 错误: Oracle 容器未运行"
    echo "请先启动 Oracle 容器: docker start oracle11g"
    exit 1
fi

echo "✓ Oracle 容器正在运行"
echo ""

# 步骤 1: 启用 Oracle CDC
echo "=========================================="
echo "步骤 1/3: 启用 Oracle CDC"
echo "=========================================="
./execute-sql-sysdba.sh sql/01-enable-oracle-cdc.sql
if [ $? -ne 0 ]; then
    echo "❌ 步骤 1 失败"
    exit 1
fi
echo ""

# 步骤 2: 创建 CDC 元数据表
echo "=========================================="
echo "步骤 2/3: 创建 CDC 元数据表"
echo "=========================================="
./execute-sql-sysdba.sh sql/02-setup-cdc-metadata.sql
if [ $? -ne 0 ]; then
    echo "❌ 步骤 2 失败"
    exit 1
fi
echo ""

# 步骤 3: 创建业务元数据表
echo "=========================================="
echo "步骤 3/3: 创建业务元数据表"
echo "=========================================="
./execute-sql.sh sql/03-setup-metadata-tables.sql system helowin helowin
if [ $? -ne 0 ]; then
    echo "❌ 步骤 3 失败"
    exit 1
fi
echo ""

# 验证配置
echo "=========================================="
echo "验证配置"
echo "=========================================="
./execute-sql.sh sql/04-check-cdc-status.sql system helowin helowin | head -150
echo ""

echo "=========================================="
echo "✅ Oracle CDC 配置完成！"
echo "=========================================="
echo ""
echo "配置摘要:"
echo "  - 归档日志: ARCHIVELOG"
echo "  - 补充日志: MIN=YES, ALL=YES"
echo "  - CDC 表位置: FINANCE_USER schema"
echo "  - 表空间: TRANS_TBS"
echo ""
echo "下一步:"
echo "  1. 构建 Docker 镜像: ./build-flink-images-cn.sh"
echo "  2. 启动服务: docker-compose up -d"
echo "  3. 访问前端: http://localhost:3000"
echo ""
echo "=========================================="
