#!/bin/bash
# 测试 Oracle CDC - 插入测试数据到 FINANCE_USER.TRANS_INFO 表

echo "========================================="
echo "测试 Oracle CDC"
echo "========================================="

# Oracle 连接信息
ORACLE_HOST="localhost"
ORACLE_PORT="1521"
ORACLE_SID="helowin"
ORACLE_USER="system"
ORACLE_PASSWORD="helowin"

echo ""
echo "1. 连接到 Oracle 数据库..."
echo "   主机: $ORACLE_HOST:$ORACLE_PORT"
echo "   SID: $ORACLE_SID"
echo "   用户: $ORACLE_USER"
echo ""

# 插入测试数据
echo "2. 插入测试数据到 FINANCE_USER.TRANS_INFO..."
docker exec oracle11g sqlplus -S $ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_SID <<EOF
SET SERVEROUTPUT ON;
SET ECHO ON;

-- 插入测试数据
INSERT INTO FINANCE_USER.TRANS_INFO (TRANS_ID, TRANS_TYPE, AMOUNT, TRANS_DATE, DESCRIPTION)
VALUES (SEQ_TRANS_ID.NEXTVAL, 'DEPOSIT', 1000.00, SYSDATE, 'Test CDC Insert');

INSERT INTO FINANCE_USER.TRANS_INFO (TRANS_ID, TRANS_TYPE, AMOUNT, TRANS_DATE, DESCRIPTION)
VALUES (SEQ_TRANS_ID.NEXTVAL, 'WITHDRAWAL', 500.00, SYSDATE, 'Test CDC Insert 2');

COMMIT;

-- 显示插入的数据
SELECT COUNT(*) as TOTAL_RECORDS FROM FINANCE_USER.TRANS_INFO;
SELECT * FROM (SELECT * FROM FINANCE_USER.TRANS_INFO ORDER BY TRANS_DATE DESC) WHERE ROWNUM <= 5;

EXIT;
EOF

echo ""
echo "3. 等待 CDC 捕获事件 (10秒)..."
sleep 10

echo ""
echo "4. 检查输出文件..."
if [ -d "output/cdc" ]; then
    echo "   输出目录存在"
    find output/cdc -type f -name "part-*" -exec echo "   找到文件: {}" \; -exec wc -l {} \;
else
    echo "   输出目录不存在"
fi

echo ""
echo "========================================="
echo "测试完成"
echo "========================================="
