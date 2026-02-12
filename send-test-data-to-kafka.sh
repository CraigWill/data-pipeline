#!/bin/bash
# 向 Kafka 发送测试 CDC 数据

set -e

echo "=========================================="
echo "向 Kafka 发送测试 CDC 数据"
echo "=========================================="
echo ""

TOPIC="cdc-events"
KAFKA_CONTAINER="kafka"

echo "Kafka Topic: $TOPIC"
echo ""

# 检查 Kafka 是否运行
if ! docker ps | grep -q $KAFKA_CONTAINER; then
    echo "❌ Kafka 容器未运行"
    exit 1
fi

echo "✅ Kafka 容器运行中"
echo ""

# 生成测试数据
echo "生成测试 CDC 事件..."
echo ""

for i in {1..10}; do
    TIMESTAMP=$(date +%s)000
    EVENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
    
    # 创建 ChangeEvent JSON
    JSON=$(cat <<EOF
{
  "type": "INSERT",
  "database": "helowin",
  "table": "trans_info",
  "timestamp": $TIMESTAMP,
  "before": null,
  "after": {
    "id": $i,
    "trans_no": "TXN$(printf '%06d' $i)",
    "amount": $((RANDOM % 10000 + 100)),
    "status": "COMPLETED",
    "create_time": "$(date -u +"%Y-%m-%d %H:%M:%S")"
  },
  "primaryKeys": ["id"],
  "eventId": "$EVENT_ID"
}
EOF
)
    
    # 发送到 Kafka
    echo "$JSON" | docker exec -i $KAFKA_CONTAINER kafka-console-producer \
        --bootstrap-server localhost:9092 \
        --topic $TOPIC \
        2>/dev/null
    
    echo "✅ 发送事件 $i: INSERT trans_info id=$i"
    sleep 0.5
done

echo ""
echo "=========================================="
echo "完成！已发送 10 条测试事件"
echo "=========================================="
echo ""
echo "验证数据:"
echo "  docker exec kafka kafka-console-consumer \\"
echo "    --bootstrap-server localhost:9092 \\"
echo "    --topic $TOPIC \\"
echo "    --from-beginning \\"
echo "    --max-messages 5"
echo ""
echo "查看 Flink 输出:"
echo "  docker exec realtime-pipeline-taskmanager-1 \\"
echo "    find /opt/flink/output/cdc -type f -name 'part-*'"
echo ""
