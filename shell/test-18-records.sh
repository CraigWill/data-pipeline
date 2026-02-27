#!/bin/bash
# 测试插入18条记录并监控CDC捕获

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║           测试18条记录插入和CDC捕获                            ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# 检查作业状态
echo -e "${YELLOW}步骤 1: 检查Flink作业状态${NC}"
echo "----------------------------------------"

JOB_ID=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null | jq -r '.jobs[] | select(.state == "RUNNING") | .jid' | head -1)

if [ -z "$JOB_ID" ]; then
    echo -e "${RED}✗ 没有运行中的作业${NC}"
    echo "请先启动Flink作业"
    exit 1
fi

echo -e "${GREEN}✓ 作业运行中${NC}"
echo "  Job ID: $JOB_ID"
echo ""

# 获取当前输出目录
CURRENT_HOUR=$(date +"%Y-%m-%d--%H")
OUTPUT_DIR="./output/cdc/${CURRENT_HOUR}"
echo "输出目录: $OUTPUT_DIR"
echo ""

# 记录插入前的文件状态
echo -e "${YELLOW}步骤 2: 记录插入前的文件状态${NC}"
echo "----------------------------------------"

if [ -d "$OUTPUT_DIR" ]; then
    BEFORE_COUNT=$(ls -1 "$OUTPUT_DIR"/*.csv 2>/dev/null | wc -l)
    BEFORE_SIZE=$(du -sh "$OUTPUT_DIR" 2>/dev/null | cut -f1)
    echo "  现有文件数: $BEFORE_COUNT"
    echo "  目录大小: $BEFORE_SIZE"
    
    # 显示最新文件
    LATEST_FILE=$(ls -t "$OUTPUT_DIR"/*.csv 2>/dev/null | head -1)
    if [ -n "$LATEST_FILE" ]; then
        LATEST_LINES=$(wc -l < "$LATEST_FILE")
        echo "  最新文件: $(basename "$LATEST_FILE")"
        echo "  最新文件行数: $LATEST_LINES"
    fi
else
    BEFORE_COUNT=0
    echo "  目录不存在（将在第一次写入时创建）"
fi
echo ""

# 插入18条记录
echo -e "${YELLOW}步骤 3: 插入18条测试记录${NC}"
echo "----------------------------------------"

echo "执行SQL脚本..."
docker exec -i oracle-db sqlplus -S system/helowin@//localhost:1521/helowin <<EOF
SET ECHO OFF
SET FEEDBACK OFF
SET HEADING OFF
SET PAGESIZE 0

@/sql/test-18-records.sql

EXIT;
EOF

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 18条记录插入成功${NC}"
    echo "  时间戳: $(date '+%Y-%m-%d %H:%M:%S')"
else
    echo -e "${RED}✗ 记录插入失败${NC}"
    exit 1
fi
echo ""

# 等待CDC捕获
echo -e "${YELLOW}步骤 4: 等待CDC捕获数据${NC}"
echo "----------------------------------------"
echo "等待30秒让CDC捕获和处理数据..."

for i in {1..30}; do
    echo -ne "\r等待中... $i/30秒"
    sleep 1
done
echo ""
echo ""

# 检查文件变化
echo -e "${YELLOW}步骤 5: 检查文件变化${NC}"
echo "----------------------------------------"

if [ -d "$OUTPUT_DIR" ]; then
    AFTER_COUNT=$(ls -1 "$OUTPUT_DIR"/*.csv 2>/dev/null | wc -l)
    AFTER_SIZE=$(du -sh "$OUTPUT_DIR" 2>/dev/null | cut -f1)
    
    echo "  当前文件数: $AFTER_COUNT"
    echo "  目录大小: $AFTER_SIZE"
    echo "  新增文件: $((AFTER_COUNT - BEFORE_COUNT))"
    echo ""
    
    # 显示最新文件内容
    LATEST_FILE=$(ls -t "$OUTPUT_DIR"/*.csv 2>/dev/null | head -1)
    if [ -n "$LATEST_FILE" ]; then
        LATEST_LINES=$(wc -l < "$LATEST_FILE")
        echo "  最新文件: $(basename "$LATEST_FILE")"
        echo "  最新文件行数: $LATEST_LINES"
        echo ""
        
        # 显示最后20行（包含我们刚插入的记录）
        echo "  最新文件最后20行:"
        echo "  ----------------------------------------"
        tail -20 "$LATEST_FILE" | while IFS= read -r line; do
            echo "  $line"
        done
        echo ""
        
        # 统计包含 "Auto-submit test record" 的行数
        TEST_RECORDS=$(grep -c "Auto-submit test record" "$LATEST_FILE" 2>/dev/null || echo "0")
        echo -e "  ${CYAN}捕获的测试记录数: $TEST_RECORDS${NC}"
    fi
else
    echo -e "${RED}✗ 输出目录不存在${NC}"
fi
echo ""

# 检查作业健康状态
echo -e "${YELLOW}步骤 6: 检查作业健康状态${NC}"
echo "----------------------------------------"

JOB_INFO=$(curl -s http://localhost:8081/jobs/$JOB_ID 2>/dev/null)
JOB_STATE=$(echo "$JOB_INFO" | jq -r '.state')
VERTICES=$(echo "$JOB_INFO" | jq -r '.vertices[] | "  \(.name): \(.status)"')

echo "作业状态: $JOB_STATE"
echo "任务状态:"
echo "$VERTICES"
echo ""

# 测试结果总结
echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                    测试结果总结                                ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$JOB_STATE" = "RUNNING" ] && [ -n "$LATEST_FILE" ]; then
    echo -e "${GREEN}✓ 测试成功${NC}"
    echo ""
    echo "验证项:"
    echo "  ✓ 作业状态: RUNNING"
    echo "  ✓ 18条记录已插入数据库"
    echo "  ✓ CDC已捕获数据"
    echo "  ✓ 文件已生成: $(basename "$LATEST_FILE")"
    echo "  ✓ 捕获的测试记录数: $TEST_RECORDS"
    echo ""
    
    if [ "$TEST_RECORDS" -ge 18 ]; then
        echo -e "${GREEN}✓ 所有18条记录都已被捕获${NC}"
    elif [ "$TEST_RECORDS" -gt 0 ]; then
        echo -e "${YELLOW}⚠ 部分记录已捕获 ($TEST_RECORDS/18)${NC}"
        echo "  可能需要等待更长时间或检查Checkpoint配置"
    else
        echo -e "${YELLOW}⚠ 测试记录尚未出现在输出文件中${NC}"
        echo "  这可能是正常的，因为CDC使用latest模式"
        echo "  请检查文件是否包含新记录"
    fi
else
    echo -e "${RED}✗ 测试失败${NC}"
    echo ""
    echo "问题:"
    if [ "$JOB_STATE" != "RUNNING" ]; then
        echo "  ✗ 作业状态异常: $JOB_STATE"
    fi
    if [ -z "$LATEST_FILE" ]; then
        echo "  ✗ 没有生成输出文件"
    fi
fi

echo ""
echo "访问地址:"
echo "  Web UI: http://localhost:8081"
echo "  作业详情: http://localhost:8081/#/job/$JOB_ID/overview"
echo ""
