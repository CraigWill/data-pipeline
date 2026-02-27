#!/bin/bash
# 启动 Flink CDC 3.x 作业
# 用法: ./shell/start-flink-cdc.sh

set -e

echo "=========================================="
echo "启动 Flink CDC 3.x Oracle 作业"
echo "=========================================="
echo ""

# 检查 Flink 集群状态
echo "检查 Flink 集群状态..."
if ! curl -s http://localhost:8081/overview > /dev/null 2>&1; then
    echo "错误: Flink 集群未运行"
    echo "请先启动 Flink: docker-compose up -d jobmanager taskmanager"
    exit 1
fi

echo "✓ Flink 集群运行中"
echo ""

# 检查是否已有运行中的作业
RUNNING_JOBS=$(curl -s http://localhost:8081/jobs/overview | python3 -c "import sys, json; data=json.load(sys.stdin); print(len([j for j in data.get('jobs', []) if j.get('state') == 'RUNNING']))" 2>/dev/null || echo "0")

if [ "$RUNNING_JOBS" != "0" ]; then
    echo "警告: 已有 $RUNNING_JOBS 个作业在运行"
    echo "当前运行的作业:"
    curl -s http://localhost:8081/jobs/overview | python3 -m json.tool | grep -E '"name"|"jid"|"state"' | head -20
    echo ""
    read -p "是否继续提交新作业? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消"
        exit 0
    fi
fi

# 提交作业
echo "提交 Flink CDC 3.x 作业..."
JOB_OUTPUT=$(docker exec flink-jobmanager flink run -d \
    -c com.realtime.pipeline.FlinkCDC3App \
    /opt/flink/usrlib/realtime-data-pipeline.jar 2>&1)

if echo "$JOB_OUTPUT" | grep -q "Job has been submitted"; then
    JOB_ID=$(echo "$JOB_OUTPUT" | grep "JobID" | awk '{print $NF}')
    echo "✓ 作业提交成功"
    echo "  Job ID: $JOB_ID"
    echo ""
    
    # 等待作业启动
    echo "等待作业启动..."
    sleep 5
    
    # 检查作业状态
    JOB_STATE=$(curl -s "http://localhost:8081/jobs/$JOB_ID" | python3 -c "import sys, json; print(json.load(sys.stdin).get('state', 'UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
    
    if [ "$JOB_STATE" = "RUNNING" ]; then
        echo "✓ 作业运行中"
        echo ""
        echo "=========================================="
        echo "Flink CDC 3.x 作业已启动"
        echo "=========================================="
        echo ""
        echo "作业信息:"
        echo "  Job ID: $JOB_ID"
        echo "  状态: $JOB_STATE"
        echo "  Web UI: http://localhost:8081/#/job/$JOB_ID/overview"
        echo ""
        echo "输出路径: ./output/cdc/"
        echo ""
        echo "监控命令:"
        echo "  查看作业状态: curl -s http://localhost:8081/jobs/$JOB_ID | python3 -m json.tool"
        echo "  查看输出文件: ls -lht output/cdc/"
        echo "  查看日志: docker logs flink-taskmanager --tail 50"
        echo ""
    else
        echo "警告: 作业状态为 $JOB_STATE"
        echo "请检查日志: docker logs flink-jobmanager --tail 50"
    fi
else
    echo "错误: 作业提交失败"
    echo "$JOB_OUTPUT"
    exit 1
fi
