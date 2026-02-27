#!/bin/bash

echo "=== 快速测试 CDC 文件生成 ==="
echo ""
echo "这个脚本将："
echo "1. 向数据库插入 5 条测试数据"
echo "2. 等待 40 秒让 Flink 处理"
echo "3. 检查是否生成了 CSV 文件"
echo ""
read -p "按 Enter 继续..."

# 插入测试数据
echo ""
echo "正在插入测试数据..."

# 查找 Oracle 容器
ORACLE_CONTAINER=$(docker ps --format "{{.Names}}" | grep -i oracle | head -1)

if [ -z "$ORACLE_CONTAINER" ]; then
    echo "❌ 未找到 Oracle 容器"
    exit 1
fi

docker exec $ORACLE_CONTAINER bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S system/helowin <<EOF > /dev/null 2>&1
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS)
VALUES (SEQ_TRANS_INFO.NEXTVAL, 'QUICK_TEST_1', 111.11, SYSTIMESTAMP, 'TEST', 'MERCHANT_TEST', 'SUCCESS');
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS)
VALUES (SEQ_TRANS_INFO.NEXTVAL, 'QUICK_TEST_2', 222.22, SYSTIMESTAMP, 'TEST', 'MERCHANT_TEST', 'SUCCESS');
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS)
VALUES (SEQ_TRANS_INFO.NEXTVAL, 'QUICK_TEST_3', 333.33, SYSTIMESTAMP, 'TEST', 'MERCHANT_TEST', 'SUCCESS');
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS)
VALUES (SEQ_TRANS_INFO.NEXTVAL, 'QUICK_TEST_4', 444.44, SYSTIMESTAMP, 'TEST', 'MERCHANT_TEST', 'SUCCESS');
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS)
VALUES (SEQ_TRANS_INFO.NEXTVAL, 'QUICK_TEST_5', 555.55, SYSTIMESTAMP, 'TEST', 'MERCHANT_TEST', 'SUCCESS');
COMMIT;
EXIT;
EOF
"

if [ $? -eq 0 ]; then
    echo "✅ 成功插入 5 条测试数据"
else
    echo "❌ 插入数据失败"
    exit 1
fi

# 等待处理
echo ""
echo "等待 40 秒让 Flink 处理数据并生成文件..."
for i in {40..1}; do
    printf "\r剩余 %2d 秒...  " $i
    sleep 1
done
echo ""

# 检查文件
echo ""
echo "=== 检查结果 ==="
echo ""

echo "1. 宿主机 output/cdc 目录:"
if ls output/cdc/*.csv 2>/dev/null | head -5; then
    echo "✅ 找到 CSV 文件！"
    echo ""
    echo "文件内容预览:"
    head -10 $(ls output/cdc/*.csv 2>/dev/null | head -1)
else
    echo "❌ 没有找到 CSV 文件"
fi

echo ""
echo "2. 容器内 /opt/flink/output/cdc 目录:"
docker exec realtime-pipeline-taskmanager-1 ls -lh /opt/flink/output/cdc/ 2>/dev/null

echo ""
echo "3. 搜索所有 CSV 文件:"
FILE_COUNT=$(docker exec realtime-pipeline-taskmanager-1 find /opt/flink/output -type f -name "*.csv" 2>/dev/null | wc -l)
echo "找到 $FILE_COUNT 个 CSV 文件"

if [ "$FILE_COUNT" -gt "0" ]; then
    echo ""
    echo "文件列表:"
    docker exec realtime-pipeline-taskmanager-1 find /opt/flink/output -type f -name "*.csv" 2>/dev/null
fi

echo ""
echo "4. 检查最新的 CDC 指标:"
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep "totalCapturedDmlCount" | tail -1 | grep -o "totalCapturedDmlCount=[0-9]*" || echo "无法获取指标"

echo ""
echo "=== 测试完成 ==="
echo ""

if [ "$FILE_COUNT" -gt "0" ]; then
    echo "✅ 成功！CDC 正在正常工作并生成 CSV 文件。"
else
    echo "❌ 失败！没有生成 CSV 文件。"
    echo ""
    echo "可能的原因："
    echo "1. 数据被表过滤器过滤掉了"
    echo "2. FileSink 配置问题"
    echo "3. 需要更长的等待时间"
    echo ""
    echo "建议："
    echo "- 查看详细诊断: ./shell/diagnose-cdc-issue.sh"
    echo "- 查看解决方案: cat ./md/CSV_FILE_NOT_GENERATED_SOLUTION.md"
fi
