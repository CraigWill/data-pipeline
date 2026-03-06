#!/bin/bash
# 测试表列表 API，检查是否有重复

set -e

MONITOR_URL=${MONITOR_URL:-http://localhost:5001}
DATASOURCE_ID=${DATASOURCE_ID:-oracle-prod}
SCHEMA=${SCHEMA:-FINANCE_USER}

echo "=========================================="
echo "测试表列表 API"
echo "=========================================="
echo "Monitor URL: ${MONITOR_URL}"
echo "Datasource ID: ${DATASOURCE_ID}"
echo "Schema: ${SCHEMA}"
echo ""

# 1. 获取表列表
echo "1. 获取表列表..."
RESPONSE=$(curl -s "${MONITOR_URL}/api/datasources/${DATASOURCE_ID}/schemas/${SCHEMA}/tables")

echo "API 响应:"
echo "$RESPONSE" | python3 -m json.tool

# 2. 检查是否有重复
echo ""
echo "2. 检查重复表名..."
TABLE_NAMES=$(echo "$RESPONSE" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('success') and data.get('data'):
    tables = data['data']
    names = [t['name'] for t in tables]
    print('\n'.join(names))
")

echo "表名列表:"
echo "$TABLE_NAMES"

echo ""
echo "重复检查:"
echo "$TABLE_NAMES" | sort | uniq -d

DUPLICATES=$(echo "$TABLE_NAMES" | sort | uniq -d | wc -l)
if [ "$DUPLICATES" -gt 0 ]; then
    echo ""
    echo "❌ 发现 $DUPLICATES 个重复的表名"
else
    echo ""
    echo "✓ 没有重复的表名"
fi

# 3. 显示表的详细信息
echo ""
echo "3. 表详细信息:"
echo "$RESPONSE" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('success') and data.get('data'):
    tables = data['data']
    print(f'共 {len(tables)} 个表:')
    for t in tables:
        print(f\"  - {t['name']}: {t['rows']} 行, {t['columns']} 列\")
"

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
