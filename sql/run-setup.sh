#!/bin/bash

# ============================================
# Oracle CDC 完整配置脚本执行器
# 按顺序执行 00-04 脚本
# ============================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
ORACLE_HOST="${ORACLE_HOST:-localhost}"
ORACLE_PORT="${ORACLE_PORT:-1521}"
ORACLE_SID="${ORACLE_SID:-helowin}"
ORACLE_SYSTEM_USER="${ORACLE_SYSTEM_USER:-system}"
ORACLE_SYSTEM_PASSWORD="${ORACLE_SYSTEM_PASSWORD:-helowin}"
ORACLE_FINANCE_USER="${ORACLE_FINANCE_USER:-finance_user}"
ORACLE_FINANCE_PASSWORD="${ORACLE_FINANCE_PASSWORD}"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Oracle CDC 配置脚本执行器${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# 检查 sqlplus 是否可用
if ! command -v sqlplus &> /dev/null; then
    echo -e "${RED}错误: sqlplus 未安装或不在 PATH 中${NC}"
    echo "请安装 Oracle Instant Client 或 Oracle Database Client"
    exit 1
fi

echo -e "${GREEN}✓ sqlplus 已安装${NC}"
echo ""

# 显示配置
echo "配置信息:"
echo "  主机: $ORACLE_HOST"
echo "  端口: $ORACLE_PORT"
echo "  SID: $ORACLE_SID"
echo "  SYSTEM 用户: $ORACLE_SYSTEM_USER"
echo "  FINANCE_USER: $ORACLE_FINANCE_USER"
echo ""

# 确认执行
read -p "是否继续执行配置脚本？(y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "取消执行"
    exit 0
fi

# 连接字符串
SYSTEM_CONN="${ORACLE_SYSTEM_USER}/${ORACLE_SYSTEM_PASSWORD}@${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_SID}"
FINANCE_CONN="${ORACLE_FINANCE_USER}/${ORACLE_FINANCE_PASSWORD}@${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_SID}"

# ============================================
# 步骤 0: 清理 FLINK_USER（可选）
# ============================================
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}步骤 0: 清理 FLINK_USER（可选）${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
read -p "是否需要清理 FLINK_USER 和 FLINK_TBS？(y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "执行 00-cleanup-flink-user.sql..."
    sqlplus -S "${SYSTEM_CONN} as sysdba" @00-cleanup-flink-user.sql
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 步骤 0 完成${NC}"
    else
        echo -e "${RED}✗ 步骤 0 失败${NC}"
        exit 1
    fi
else
    echo "跳过步骤 0"
fi

# ============================================
# 步骤 1: 启用 Oracle CDC
# ============================================
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}步骤 1: 启用 Oracle CDC${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "${YELLOW}警告: 此步骤将重启数据库以启用归档日志模式${NC}"
echo -e "${YELLOW}请确保没有重要的业务正在运行${NC}"
echo ""
read -p "是否继续？(y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "跳过步骤 1"
    echo "如果归档日志已启用，可以继续执行后续步骤"
else
    echo "执行 01-enable-oracle-cdc.sql..."
    sqlplus -S "${SYSTEM_CONN} as sysdba" @01-enable-oracle-cdc.sql
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 步骤 1 完成${NC}"
    else
        echo -e "${RED}✗ 步骤 1 失败${NC}"
        exit 1
    fi
fi

# ============================================
# 步骤 2: 创建 CDC 基础设施
# ============================================
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}步骤 2: 创建 CDC 基础设施${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "执行 02-setup-cdc-metadata.sql..."
sqlplus -S "${SYSTEM_CONN} as sysdba" @02-setup-cdc-metadata.sql
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 步骤 2 完成${NC}"
else
    echo -e "${RED}✗ 步骤 2 失败${NC}"
    exit 1
fi

# ============================================
# 步骤 3: 创建元数据表
# ============================================
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}步骤 3: 创建元数据表${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# 检查是否提供了 FINANCE_USER 密码
if [ -z "$ORACLE_FINANCE_PASSWORD" ]; then
    echo -e "${YELLOW}未设置 ORACLE_FINANCE_PASSWORD 环境变量${NC}"
    echo "尝试使用 SYSTEM 用户执行..."
    sqlplus -S "${SYSTEM_CONN}" @03-setup-metadata-tables.sql
else
    echo "使用 FINANCE_USER 执行..."
    sqlplus -S "${FINANCE_CONN}" @03-setup-metadata-tables.sql
fi

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 步骤 3 完成${NC}"
else
    echo -e "${RED}✗ 步骤 3 失败${NC}"
    exit 1
fi

# ============================================
# 步骤 4: 检查 CDC 状态
# ============================================
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}步骤 4: 检查 CDC 状态${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "执行 04-check-cdc-status.sql..."
sqlplus -S "${SYSTEM_CONN}" @04-check-cdc-status.sql

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 步骤 4 完成${NC}"
else
    echo -e "${YELLOW}⚠ 步骤 4 有警告，请检查输出${NC}"
fi

# ============================================
# 完成
# ============================================
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}✅ Oracle CDC 配置完成！${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "配置摘要:"
echo "  ✓ 归档日志模式已启用"
echo "  ✓ 补充日志已启用"
echo "  ✓ FINANCE_USER 权限已授予"
echo "  ✓ LOG_MINING_FLUSH 表已创建"
echo "  ✓ 元数据表已创建（CDC_DATASOURCES, CDC_TASKS, RUNTIME_JOBS）"
echo ""
echo "下一步:"
echo "  1. 启动 Flink 集群: ./start.sh"
echo "  2. 访问监控界面: http://localhost:8888"
echo "  3. 创建数据源和 CDC 任务"
echo ""
echo -e "${BLUE}============================================${NC}"
