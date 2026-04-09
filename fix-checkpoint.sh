#!/bin/bash

# Flink Checkpoint 修复脚本
# 用于清理损坏的 checkpoint 并重启作业

set -e

echo "=========================================="
echo "Flink Checkpoint 修复工具"
echo "=========================================="
echo ""

# 检查是否提供了 Job ID
if [ -z "$1" ]; then
    echo "用法: $0 <job-id> [选项]"
    echo ""
    echo "选项:"
    echo "  --clean-all    清理所有 checkpoint 数据"
    echo "  --restart      清理后自动重启作业"
    echo ""
    echo "示例:"
    echo "  $0 a7d068b5e7c0572848ad6f67f6c457d2"
    echo "  $0 a7d068b5e7c0572848ad6f67f6c457d2 --clean-all"
    echo "  $0 a7d068b5e7c0572848ad6f67f6c457d2 --restart"
    echo ""
    exit 1
fi

JOB_ID=$1
CLEAN_ALL=false
RESTART=false

# 解析选项
shift
while [ $# -gt 0 ]; do
    case "$1" in
        --clean-all)
            CLEAN_ALL=true
            ;;
        --restart)
            RESTART=true
            ;;
        *)
            echo "未知选项: $1"
            exit 1
            ;;
    esac
    shift
done

echo "Job ID: $JOB_ID"
echo "清理所有 checkpoint: $CLEAN_ALL"
echo "自动重启: $RESTART"
echo ""

# Checkpoint 存储路径
CHECKPOINT_DIR="/tmp/flink-checkpoints-directory"

echo "步骤 1: 检查 checkpoint 目录..."
if docker exec flink-jobmanager test -d "$CHECKPOINT_DIR/$JOB_ID"; then
    echo "✅ 找到 checkpoint 目录: $CHECKPOINT_DIR/$JOB_ID"
else
    echo "⚠️  未找到 checkpoint 目录，可能作业从未创建 checkpoint"
    exit 0
fi

echo ""
echo "步骤 2: 列出 checkpoint 文件..."
docker exec flink-jobmanager sh -c "ls -lh $CHECKPOINT_DIR/$JOB_ID/ | head -20"

echo ""
echo "步骤 3: 取消作业..."
CANCEL_RESULT=$(curl -s -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel" || echo "failed")
if echo "$CANCEL_RESULT" | grep -q "failed"; then
    echo "⚠️  作业可能已经停止或不存在"
else
    echo "✅ 作业取消请求已发送"
    echo "等待作业完全停止..."
    sleep 5
fi

echo ""
if [ "$CLEAN_ALL" = true ]; then
    echo "步骤 4: 清理所有 checkpoint 数据..."
    docker exec flink-jobmanager sh -c "rm -rf $CHECKPOINT_DIR/$JOB_ID"
    echo "✅ 已删除整个 checkpoint 目录"
else
    echo "步骤 4: 清理损坏的 checkpoint..."
    # 只删除最新的几个 checkpoint（通常是损坏的）
    docker exec flink-jobmanager sh -c "cd $CHECKPOINT_DIR/$JOB_ID && ls -t | head -5 | xargs rm -rf"
    echo "✅ 已删除最近的 5 个 checkpoint"
fi

echo ""
echo "步骤 5: 验证清理结果..."
if docker exec flink-jobmanager test -d "$CHECKPOINT_DIR/$JOB_ID"; then
    echo "剩余文件:"
    docker exec flink-jobmanager sh -c "ls -lh $CHECKPOINT_DIR/$JOB_ID/ | head -10"
else
    echo "✅ Checkpoint 目录已完全清理"
fi

if [ "$RESTART" = true ]; then
    echo ""
    echo "步骤 6: 重启作业..."
    echo "⚠️  注意: 作业将从头开始，不会从 checkpoint 恢复"
    echo ""
    echo "请手动通过以下方式重启作业:"
    echo "1. 前端界面: CDC 任务列表 -> 提交任务"
    echo "2. API: POST /api/cdc/tasks/{taskId}/submit"
    echo ""
fi

echo ""
echo "=========================================="
echo "修复完成"
echo "=========================================="
echo ""
echo "后续步骤:"
echo "1. 如果需要重启作业，请通过前端或 API 提交"
echo "2. 新作业将从头开始处理数据"
echo "3. 监控作业运行状态，确保 checkpoint 正常创建"
echo ""
echo "预防措施:"
echo "- 确保有足够的磁盘空间用于 checkpoint"
echo "- 定期清理旧的 checkpoint 数据"
echo "- 监控 Flink 日志，及时发现问题"
