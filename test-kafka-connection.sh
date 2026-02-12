#!/bin/bash
# Kafka 连接测试脚本

echo "=========================================="
echo "Kafka 连接测试"
echo "=========================================="
echo ""

echo "1. 测试端口连接..."
if nc -zv localhost 9092 2>&1 | grep -q succeeded; then
    echo "   ✅ 端口 9092 可访问"
else
    echo "   ❌ 端口 9092 不可访问"
    exit 1
fi
echo ""

echo "2. 列出 Topics..."
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092 2>/dev/null
echo ""

echo "3. 查看 cdc-events Topic 详情..."
docker exec kafka kafka-topics --describe --topic cdc-events --bootstrap-server localhost:9092 2>/dev/null
echo ""

echo "4. 测试生产消息..."
echo "test-message-$(date +%s)" | docker exec -i kafka kafka-console-producer \
    --topic cdc-events \
    --bootstrap-server localhost:9092 2>/dev/null
echo "   ✅ 消息已发送"
echo ""

echo "5. 测试消费消息（最后一条）..."
docker exec kafka kafka-console-consumer \
    --topic cdc-events \
    --bootstrap-server localhost:9092 \
    --from-beginning \
    --max-messages 1 \
    --timeout-ms 5000 2>/dev/null || true
echo ""

echo "=========================================="
echo "✅ Kafka 连接测试完成！"
echo "=========================================="
echo ""
echo "访问地址:"
echo "  - Kafka Broker: localhost:9092"
echo "  - Kafka UI: http://localhost:8080"
echo ""
