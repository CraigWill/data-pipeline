#!/bin/bash
# 查看 Flink 输出文件

echo "=========================================="
echo "Flink CDC 输出文件查看"
echo "=========================================="
echo ""

# 从容器复制最新文件
echo "从容器复制最新输出文件..."
mkdir -p output/cdc
docker cp realtime-pipeline-taskmanager-1:/opt/flink/output/cdc/. output/cdc/ 2>/dev/null
echo "✅ 文件已复制到 output/cdc/"
echo ""

# 显示目录结构
echo "输出目录结构:"
echo "----------------------------------------"
tree output/cdc/ -L 2 2>/dev/null || find output/cdc -type d | head -10
echo ""

# 统计文件
echo "文件统计:"
echo "----------------------------------------"
FILE_COUNT=$(find output/cdc -type f -name "*.inprogress.*" -o -name "part-*" | grep -v ".crc" | wc -l | tr -d ' ')
echo "总文件数: $FILE_COUNT"
echo ""

# 显示最新的文件
echo "最新的输出文件:"
echo "----------------------------------------"
LATEST_FILE=$(find output/cdc -type f \( -name "*.inprogress.*" -o -name "part-*" \) ! -name "*.crc" -print0 | xargs -0 ls -t | head -1)

if [ -n "$LATEST_FILE" ]; then
    echo "文件: $LATEST_FILE"
    FILE_SIZE=$(ls -lh "$LATEST_FILE" | awk '{print $5}')
    echo "大小: $FILE_SIZE"
    echo ""
    
    # 显示文件内容
    echo "文件内容（前 20 行）:"
    echo "----------------------------------------"
    cat "$LATEST_FILE" | head -20
    echo ""
    echo "..."
    echo ""
    
    # 统计记录数
    RECORD_COUNT=$(cat "$LATEST_FILE" | wc -l | tr -d ' ')
    echo "总记录数: $RECORD_COUNT"
else
    echo "❌ 没有找到输出文件"
fi

echo ""
echo "=========================================="
echo "查看完整文件:"
echo "  cat $LATEST_FILE"
echo ""
echo "实时监控新数据:"
echo "  docker exec realtime-pipeline-taskmanager-1 \\"
echo "    sh -c 'tail -f /opt/flink/output/cdc/2026-*/.*inprogress.*'"
echo ""
echo "查看所有文件:"
echo "  find output/cdc -type f -name '*.inprogress.*'"
echo "=========================================="
