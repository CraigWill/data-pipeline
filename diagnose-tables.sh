#!/bin/bash
# 诊断表重复问题

echo "=========================================="
echo "诊断表重复问题"
echo "=========================================="

# 1. 调用 API
echo ""
echo "1. API 返回的表列表:"
curl -s http://localhost:5001/api/datasources/oracle-prod/schemas/FINANCE_USER/tables | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('success'):
    tables = data['data']
    print(f'共 {len(tables)} 条记录:')
    for i, t in enumerate(tables, 1):
        print(f\"  {i}. {t['name']}: {t['rows']} 行, {t['columns']} 列\")
    
    # 检查重复
    names = [t['name'] for t in tables]
    from collections import Counter
    counts = Counter(names)
    duplicates = {name: count for name, count in counts.items() if count > 1}
    if duplicates:
        print(f'\n❌ 发现重复:')
        for name, count in duplicates.items():
            print(f'  - {name}: 出现 {count} 次')
    else:
        print('\n✓ 没有重复')
"

# 2. 检查后端日志
echo ""
echo "2. 后端日志 (最近的表查询):"
docker logs flink-monitor-backend 2>&1 | grep -A 5 "查询 Schema" | tail -20

# 3. 检查是否重新构建
echo ""
echo "3. 检查代码是否已更新:"
docker exec flink-monitor-backend grep -A 3 "DISTINCT t.table_name" /app/app.jar > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✓ 代码已包含 DISTINCT 修复"
else
    echo "❌ 代码未更新，需要重新构建"
fi

echo ""
echo "=========================================="
echo "诊断完成"
echo "=========================================="
