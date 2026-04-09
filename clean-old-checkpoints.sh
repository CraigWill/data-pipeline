#!/bin/bash

# 清理旧的 Flink Checkpoint 数据
# 保留最近的 checkpoint，删除旧的

set -e

echo "=========================================="
echo "清理旧的 Flink Checkpoint"
echo "=========================================="
echo ""

CHECKPOINT_DIR="/opt/flink/checkpoints"
KEEP_RECENT=2  # 保留最近的 N 个作业的 checkpoint

echo "Checkpoint 目录: $CHECKPOINT_DIR"
echo "保留最近的: $KEEP_RECENT 个作业"
echo ""

echo "步骤 1: 列出所有 checkpoint 目录..."
docker exec flink-jobmanager sh -c "cd $CHECKPOINT_DIR && ls -lt | head -20"

echo ""
echo "步骤 2: 计算目录数量..."
TOTAL=$(docker exec flink-jobmanager sh -c "cd $CHECKPOINT_DIR && ls -1 | wc -l")
echo "总共有 $TOTAL 个 checkpoint 目录"

if [ "$TOTAL" -le "$KEEP_RECENT" ]; then
    echo "✅ 目录数量在限制内，无需清理"
    exit 0
fi

TO_DELETE=$((TOTAL - KEEP_RECENT))
echo "将删除 $TO_DELETE 个旧目录"

echo ""
read -p "确认删除? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "已取消"
    exit 0
fi

echo ""
echo "步骤 3: 删除旧的 checkpoint 目录..."
docker exec flink-jobmanager sh -c "cd $CHECKPOINT_DIR && ls -t | tail -n +$((KEEP_RECENT + 1)) | xargs rm -rf"

echo ""
echo "步骤 4: 验证清理结果..."
REMAINING=$(docker exec flink-jobmanager sh -c "cd $CHECKPOINT_DIR && ls -1 | wc -l")
echo "剩余 $REMAINING 个目录:"
docker exec flink-jobmanager sh -c "cd $CHECKPOINT_DIR && ls -lt"

echo ""
echo "=========================================="
echo "清理完成"
echo "=========================================="
echo ""
echo "释放的空间:"
docker exec flink-jobmanager df -h /opt/flink/checkpoints
