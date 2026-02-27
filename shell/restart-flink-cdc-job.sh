#!/bin/bash

echo "=== 重启 Flink CDC 作业 ==="
echo ""

# 1. 获取当前运行的作业 ID
echo "1. 获取当前运行的作业..."
JOB_ID=$(curl -s http://localhost:8081/jobs/overview | python3 -c "import sys, json; jobs = json.load(sys.stdin)['jobs']; print([j['jid'] for j in jobs if j['state'] == 'RUNNING'][0] if any(j['state'] == 'RUNNING' for j in jobs) else '')")

if [ -z "$JOB_ID" ]; then
    echo "❌ 没有找到运行中的作业"
    echo ""
    echo "所有作业:"
    curl -s http://localhost:8081/jobs/overview | python3 -c "import sys, json; jobs = json.load(sys.stdin)['jobs']; [print(f'  ID: {j[\"jid\"]}, 状态: {j[\"state\"]}, 名称: {j[\"name\"]}') for j in jobs]"
    echo ""
    echo "直接提交新作业..."
else
    echo "找到运行中的作业: $JOB_ID"
    
    # 获取作业详情
    curl -s "http://localhost:8081/jobs/$JOB_ID" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'  名称: {data[\"name\"]}')
print(f'  状态: {data[\"state\"]}')
print(f'  运行时长: {data[\"duration\"]//1000}秒')
"
    echo ""
    
    # 2. 取消当前作业
    echo "2. 取消当前作业..."
    CANCEL_RESULT=$(curl -s -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel")
    echo "取消请求已发送"
    
    # 等待作业取消
    echo ""
    echo "3. 等待作业取消..."
    for i in {10..1}; do
        printf "\r等待 %2d 秒...  " $i
        sleep 1
    done
    echo ""
    
    # 检查作业状态
    JOB_STATE=$(curl -s "http://localhost:8081/jobs/$JOB_ID" | python3 -c "import sys, json; print(json.load(sys.stdin).get('state', 'UNKNOWN'))" 2>/dev/null || echo "CANCELED")
    echo "作业状态: $JOB_STATE"
fi

echo ""

# 4. 提交新作业
echo "4. 提交新的 Flink CDC 作业..."
SUBMIT_RESULT=$(docker exec flink-jobmanager flink run -d \
  -c com.realtime.pipeline.FlinkCDC3App \
  /opt/flink/usrlib/realtime-data-pipeline.jar 2>&1)

if [ $? -eq 0 ]; then
    echo "✅ 作业提交成功"
    echo ""
    echo "$SUBMIT_RESULT"
else
    echo "❌ 作业提交失败"
    echo "$SUBMIT_RESULT"
    exit 1
fi

echo ""

# 5. 等待作业启动
echo "5. 等待作业启动..."
for i in {10..1}; do
    printf "\r等待 %2d 秒...  " $i
    sleep 1
done
echo ""

# 6. 获取新作业 ID 和状态
echo "6. 检查新作业状态..."
NEW_JOB_INFO=$(curl -s http://localhost:8081/jobs/overview | python3 -c "
import sys, json
jobs = json.load(sys.stdin)['jobs']
running_jobs = [j for j in jobs if j['state'] == 'RUNNING']
if running_jobs:
    job = running_jobs[0]
    print(f\"ID: {job['jid']}\")
    print(f\"名称: {job['name']}\")
    print(f\"状态: {job['state']}\")
    print(f\"开始时间: {job['start-time']}\")
else:
    print('未找到运行中的作业')
")

echo "$NEW_JOB_INFO"

# 提取新的 Job ID
NEW_JOB_ID=$(echo "$NEW_JOB_INFO" | grep "ID:" | awk '{print $2}')

if [ -z "$NEW_JOB_ID" ]; then
    echo ""
    echo "❌ 新作业未成功启动"
    echo ""
    echo "请检查 JobManager 日志:"
    echo "  docker logs flink-jobmanager --tail 50"
    exit 1
fi

echo ""
echo "=== 作业重启成功 ==="
echo ""
echo "新作业 ID: $NEW_JOB_ID"
echo ""
echo "监控命令:"
echo "  查看作业状态: curl -s http://localhost:8081/jobs/$NEW_JOB_ID | python3 -c \"import sys, json; data = json.load(sys.stdin); print(f'状态: {data[\\\"state\\\"]}, 运行时长: {data[\\\"duration\\\"]//1000}秒')\""
echo "  查看 Web UI: http://localhost:8081/#/job/$NEW_JOB_ID/overview"
echo "  查看日志: docker logs realtime-pipeline-taskmanager-1 -f"
echo ""
echo "等待 1-2 分钟后，插入测试数据验证 CDC 是否工作:"
echo "  ./shell/quick-test-cdc.sh"
