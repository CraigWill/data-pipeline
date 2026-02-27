#!/bin/bash
# 快速插入测试数据

echo "插入 3 条测试交易记录..."

# 直接使用 SQL*Plus 命令
sqlplus64 system/helowin@//host.docker.internal:1521/helowin <<EOF
INSERT INTO FINANCE_USER.TRANS_INFO VALUES ('TEST20260225001', 'ACC99999999', 1000.00, SYSTIMESTAMP, 'DEPOSIT', 'MER999999', 'SUCCESS');
INSERT INTO FINANCE_USER.TRANS_INFO VALUES ('TEST20260225002', 'ACC88888888', 2000.00, SYSTIMESTAMP, 'TRANSFER', 'MER888888', 'SUCCESS');
INSERT INTO FINANCE_USER.TRANS_INFO VALUES ('TEST20260225003', 'ACC77777777', 3000.00, SYSTIMESTAMP, 'PAY', 'MER777777', 'SUCCESS');
COMMIT;
EXIT;
EOF

echo "✅ 数据插入完成，等待 10 秒..."
sleep 10

echo "查看最新 CSV 文件内容:"
docker exec realtime-pipeline-taskmanager-1 cat '/opt/flink/output/cdc/2026-02-25--11/.part-4b0a6db6-c755-4e9f-b362-1758479a8edd-0.csv.inprogress.ce0dce50-9fee-4762-ac3b-36c5e03fb112' 2>/dev/null || echo "文件可能已关闭"

echo ""
echo "查看所有最近的文件:"
docker exec realtime-pipeline-taskmanager-1 find /opt/flink/output/cdc/2026-02-25--11/ -type f -mmin -2 -exec tail -5 {} \;
