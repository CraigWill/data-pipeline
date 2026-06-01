#!/bin/bash

# ============================================
# Oracle CDC 配置脚本执行器（Docker 版本）
# 在 Oracle 容器内执行 SQL 脚本
# ============================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
ORACLE_CONTAINER="${ORACLE_CONTAINER:-oracle11g}"
ORACLE_SID="${ORACLE_SID:-helowin}"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Oracle CDC 配置脚本执行器（Docker 版本）${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# 检查 Docker 是否运行
if ! docker ps &> /dev/null; then
    echo -e "${RED}错误: Docker 未运行或无权限访问${NC}"
    exit 1
fi

# 检查 Oracle 容器是否存在
if ! docker ps -a --format '{{.Names}}' | grep -q "^${ORACLE_CONTAINER}$"; then
    echo -e "${RED}错误: Oracle 容器 '${ORACLE_CONTAINER}' 不存在${NC}"
    echo "可用的容器:"
    docker ps -a --format "  - {{.Names}}"
    exit 1
fi

# 检查 Oracle 容器是否运行
if ! docker ps --format '{{.Names}}' | grep -q "^${ORACLE_CONTAINER}$"; then
    echo -e "${YELLOW}警告: Oracle 容器 '${ORACLE_CONTAINER}' 未运行${NC}"
    read -p "是否启动容器？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker start ${ORACLE_CONTAINER}
        echo "等待 Oracle 启动..."
        sleep 10
    else
        exit 1
    fi
fi

echo -e "${GREEN}✓ Oracle 容器运行中${NC}"
echo ""

# 显示配置
echo "配置信息:"
echo "  容器名称: $ORACLE_CONTAINER"
echo "  SID: $ORACLE_SID"
echo ""

# 确认执行
read -p "是否继续执行配置脚本？(y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "取消执行"
    exit 0
fi

# 复制 SQL 文件到容器
echo ""
echo "复制 SQL 文件到容器..."
docker cp sql/00-cleanup-flink-user.sql ${ORACLE_CONTAINER}:/tmp/
docker cp sql/01-enable-oracle-cdc.sql ${ORACLE_CONTAINER}:/tmp/
docker cp sql/02-setup-cdc-metadata.sql ${ORACLE_CONTAINER}:/tmp/
docker cp sql/03-setup-metadata-tables.sql ${ORACLE_CONTAINER}:/tmp/
docker cp sql/04-check-cdc-status.sql ${ORACLE_CONTAINER}:/tmp/
echo -e "${GREEN}✓ SQL 文件已复制${NC}"

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
    docker exec -i ${ORACLE_CONTAINER} bash -c "
        source /home/oracle/.bash_profile
        cd /tmp
        sqlplus / as sysdba @00-cleanup-flink-user.sql
    "
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
    docker exec -i ${ORACLE_CONTAINER} bash -c "
        source /home/oracle/.bash_profile
        cd /tmp
        sqlplus / as sysdba @01-enable-oracle-cdc.sql
    "
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
docker exec -i ${ORACLE_CONTAINER} bash -c "
    source /home/oracle/.bash_profile
    cd /tmp
    sqlplus / as sysdba @02-setup-cdc-metadata.sql
"
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
echo "执行 03-setup-metadata-tables.sql..."
docker exec -i ${ORACLE_CONTAINER} bash -c "
    source /home/oracle/.bash_profile
    cd /tmp
    sqlplus system/helowin@${ORACLE_SID} @03-setup-metadata-tables.sql
"
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
docker exec -i ${ORACLE_CONTAINER} bash -c "
    source /home/oracle/.bash_profile
    cd /tmp
    sqlplus system/helowin@${ORACLE_SID} @04-check-cdc-status.sql
"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 步骤 4 完成${NC}"
else
    echo -e "${YELLOW}⚠ 步骤 4 有警告，请检查输出${NC}"
fi

# 清理临时文件
echo ""
echo "清理临时文件..."
docker exec ${ORACLE_CONTAINER} rm -f /tmp/00-cleanup-flink-user.sql
docker exec ${ORACLE_CONTAINER} rm -f /tmp/01-enable-oracle-cdc.sql
docker exec ${ORACLE_CONTAINER} rm -f /tmp/02-setup-cdc-metadata.sql
docker exec ${ORACLE_CONTAINER} rm -f /tmp/03-setup-metadata-tables.sql
docker exec ${ORACLE_CONTAINER} rm -f /tmp/04-check-cdc-status.sql

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
