#!/bin/bash
# Flink CDC 3.x 状态检查脚本

echo "=== Flink CDC 3.x 状态检查 ==="
echo ""

# 检查 Flink 集群
echo "1. Flink 集群状态:"
FLINK_STATUS=$(curl -s http://localhost:8081/overview 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "   ✅ JobManager 运行中 (http://localhost:8081)"
    echo "   TaskManagers: $(echo $FLINK_STATUS | grep -o '"taskmanagers":[0-9]*' | cut -d: -f2)"
    echo "   Task Slots: $(echo $FLINK_STATUS | grep -o '"slots-available":[0-9]*' | cut -d: -f2) 可用"
else
    echo "   ❌ JobManager 无法访问"
fi
echo ""

# 检查 CDC 作业
echo "2. Flink CDC 3.x 作业状态:"
JOB_ID="6868437613383535578929d55fc63b77"
JOB_STATUS=$(curl -s http://localhost:8081/jobs/$JOB_ID 2>/dev/null)
if [ $? -eq 0 ]; then
    STATE=$(echo $JOB_STATUS | grep -o '"state":"[^"]*"' | cut -d'"' -f4)
    echo "   作业 ID: $JOB_ID"
    echo "   状态: $STATE"
    
    # 获取指标
    METRICS=$(curl -s "http://localhost:8081/jobs/$JOB_ID/vertices/cbc357ccb763df2852fee8c4fc7d55f2/metrics?get=0.Source__Oracle_CDC_3_x_Source.numRecordsOut,0.numRecordsOut" 2>/dev/null)
    RECORDS_OUT=$(echo $METRICS | grep -o '"value":"[0-9]*"' | head -1 | cut -d'"' -f4)
    echo "   输出记录数: $RECORDS_OUT"
    
    if [ "$RECORDS_OUT" = "0" ]; then
        echo "   ⚠️  作业运行中但未产生输出"
    else
        echo "   ✅ 作业正常产生输出"
    fi
else
    echo "   ❌ 作业未运行"
fi
echo ""

# 检查输出文件
echo "3. 输出文件:"
if [ -d "./output/cdc" ]; then
    FILE_COUNT=$(find ./output/cdc -name "*.csv" 2>/dev/null | wc -l | tr -d ' ')
    echo "   CSV 文件数量: $FILE_COUNT"
    echo "   最新文件:"
    ls -lht ./output/cdc/*.csv 2>/dev/null | head -3 | awk '{print "     " $9 " (" $5 ")"}'
else
    echo "   ❌ 输出目录不存在"
fi
echo ""

# 检查 Oracle 数据库
echo "4. Oracle 数据库连接:"
ORACLE_TEST=$(docker exec oracle11g bash -c "source /home/oracle/.bash_profile && echo 'SELECT COUNT(*) FROM FINANCE_USER.TRANS_INFO;' | sqlplus -s system/helowin@helowin" 2>/dev/null | grep -E '^[0-9]+$' | head -1)
if [ ! -z "$ORACLE_TEST" ]; then
    echo "   ✅ Oracle 连接正常"
    echo "   TRANS_INFO 表记录数: $ORACLE_TEST"
else
    echo "   ❌ Oracle 连接失败"
fi
echo ""

# 诊断建议
echo "5. 诊断建议:"
if [ "$STATE" = "RUNNING" ] && [ "$RECORDS_OUT" = "0" ]; then
    echo "   ⚠️  作业运行但无输出，可能原因:"
    echo "      1. Snapshot 阶段耗时较长（大表需要较长时间）"
    echo "      2. Oracle LogMiner 配置问题"
    echo "      3. 表过滤配置导致所有记录被过滤"
    echo ""
    echo "   建议操作:"
    echo "      - 查看 TaskManager 日志: docker logs realtime-pipeline-taskmanager-1"
    echo "      - 插入测试数据验证 streaming 阶段"
    echo "      - 考虑使用 StartupOptions.latest() 跳过 snapshot"
fi
echo ""

echo "=== 检查完成 ==="
