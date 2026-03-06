#!/usr/bin/env python3
"""直接查询数据库检查表"""

import cx_Oracle

# 配置
dsn = cx_Oracle.makedsn("localhost", 1521, sid="helowin")
conn = cx_Oracle.connect("system", "password", dsn)
cursor = conn.cursor()

print("=" * 60)
print("查询 FINANCE_USER 的表")
print("=" * 60)

# 查询所有表
sql = """
SELECT t.owner, t.table_name, t.num_rows, t.last_analyzed, t.temporary,
       (SELECT COUNT(*) FROM all_tab_columns c 
        WHERE c.owner = t.owner AND c.table_name = t.table_name) AS col_count
FROM all_tables t
WHERE t.owner = 'FINANCE_USER'
AND t.table_name IN ('ACCOUNT_INFO', 'TRANS_INFO', 'IDS_ACCOUNT_INFO', 'IDS_TRANS_INFO')
ORDER BY t.table_name, t.num_rows DESC
"""

cursor.execute(sql)
rows = cursor.fetchall()

print(f"\n共找到 {len(rows)} 条记录:\n")
for i, row in enumerate(rows, 1):
    owner, table_name, num_rows, last_analyzed, temporary, col_count = row
    print(f"{i}. {owner}.{table_name}")
    print(f"   行数: {num_rows if num_rows else 0}")
    print(f"   列数: {col_count}")
    print(f"   最后分析: {last_analyzed}")
    print(f"   临时表: {temporary}")
    print()

# 检查是否有 IDS_ 前缀的表
print("=" * 60)
print("检查所有表（包括 IDS_ 前缀）")
print("=" * 60)

sql2 = """
SELECT table_name, num_rows
FROM all_tables
WHERE owner = 'FINANCE_USER'
AND table_name NOT LIKE 'BIN$%'
ORDER BY table_name
"""

cursor.execute(sql2)
all_tables = cursor.fetchall()

print(f"\n共 {len(all_tables)} 个表:\n")
for table_name, num_rows in all_tables:
    print(f"  - {table_name}: {num_rows if num_rows else 0} 行")

cursor.close()
conn.close()
