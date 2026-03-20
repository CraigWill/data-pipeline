#!/bin/bash

echo "=========================================="
echo "CDC 事件监控功能测试"
echo "=========================================="
echo ""

echo "1. 测试获取表列表..."
curl -s http://localhost:5001/api/cdc/events/tables | python3 -c "import sys, json; data = json.load(sys.stdin); print('✅ 成功' if data.get('success') else '❌ 失败'); print('表列表:', data.get('data', []))"
echo ""

echo "2. 测试获取统计信息..."
curl -s http://localhost:5001/api/cdc/events/stats | python3 -c "import sys, json; data = json.load(sys.stdin); print('✅ 成功' if data.get('success') else '❌ 失败'); stats = data.get('data', {}); print(f\"总事件数: {stats.get('totalEvents', 0)}\"); print(f\"INSERT: {stats.get('insertEvents', 0)}\"); print(f\"UPDATE: {stats.get('updateEvents', 0)}\"); print(f\"DELETE: {stats.get('deleteEvents', 0)}\")"
echo ""

echo "3. 测试获取事件列表（前5条）..."
curl -s 'http://localhost:5001/api/cdc/events?page=1&size=5' | python3 -c "import sys, json; data = json.load(sys.stdin); print('✅ 成功' if data.get('success') else '❌ 失败'); result = data.get('data', {}); print(f\"总记录数: {result.get('total', 0)}\"); print(f\"总页数: {result.get('totalPages', 0)}\"); print(f\"当前页: {result.get('currentPage', 1)}\"); events = result.get('events', []); print(f\"返回事件数: {len(events)}\"); print('\\n前3个事件:'); [print(f\"  - {e.get('tableName')} | {e.get('eventType')} | {e.get('source', '')[:50]}...\") for e in events[:3]]"
echo ""

echo "4. 测试按表名过滤..."
curl -s 'http://localhost:5001/api/cdc/events?table=IDS_TRANS_INFO&page=1&size=3' | python3 -c "import sys, json; data = json.load(sys.stdin); print('✅ 成功' if data.get('success') else '❌ 失败'); result = data.get('data', {}); events = result.get('events', []); print(f\"IDS_TRANS_INFO 表事件数: {len(events)}\")"
echo ""

echo "5. 测试前端页面..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8888/events)
if [ "$STATUS" = "200" ]; then
    echo "✅ 前端页面可访问"
    echo "访问地址: http://localhost:8888/events"
else
    echo "❌ 前端页面访问失败 (HTTP $STATUS)"
fi
echo ""

echo "=========================================="
echo "测试完成！"
echo "=========================================="
echo ""
echo "📊 访问 CDC 事件监控页面:"
echo "   http://localhost:8888/events"
echo ""
echo "🔐 登录信息:"
echo "   用户名: admin"
echo "   密码: admin"
echo ""
