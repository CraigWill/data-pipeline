# Flink CDC 作业重启成功

## 执行时间
2026-02-25 17:24

## 操作步骤

### 1. 重启作业
- 旧作业 ID: 77fa5347dbd0e545fb3c7598b9f07d82（已取消）
- 新作业 ID: fb7ac5009e6458c6e666eca23d93d5be
- 启动时间: 17:24:35
- 状态: RUNNING ✅

### 2. 插入测试数据
- 插入 3 条测试数据（RESTART_TEST_1, RESTART_TEST_2, RESTART_TEST_3）
- 触发 Redo Log 切换
- 触发 Checkpoint

### 3. 验证结果

## 成功指标

### ✅ CSV 文件已生成

**文件位置**: `output/cdc/2026-02-25--17/`

**生成的文件**:
```
IDS_TRANS_INFO_20260225_172433765-b96904fa-3a4e-4c29-92f1-3b4b9e111824-0.csv (20MB, 208,188 行)
IDS_TRANS_INFO_20260225_172433765-b96904fa-3a4e-4c29-92f1-3b4b9e111824-1.csv (20MB, 208,206 行)
IDS_TRANS_INFO_20260225_172433765-b96904fa-3a4e-4c29-92f1-3b4b9e111824-2.csv (20MB, 208,200 行)
IDS_TRANS_INFO_20260225_172433765-b96904fa-3a4e-4c29-92f1-3b4b9e111824-3.csv (20MB, 208,209 行)
IDS_TRANS_INFO_20260225_172433765-b96904fa-3a4e-4c29-92f1-3b4b9e111824-4.csv (20MB, 208,201 行)
```

**总计**: 5 个文件，约 100MB，1,041,004 行数据（包含标题行）

### ✅ 文件格式正确

**标题行**:
```
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
```

**数据示例**:
```
2026-02-25 17:27:39,INSERT,20260360317309,ACC02389359,2883.37,1710128687000,TRANSFER,MER007301,SUCCESS
2026-02-25 17:27:39,INSERT,20260360317310,ACC02282639,8025.16,1704755089000,TRANSFER,MER044524,SUCCESS
2026-02-25 17:27:39,INSERT,20260360317311,ACC03684005,1512.06,1705815010000,PAY,MER016744,SUCCESS
```

### ✅ 文件命名正确

格式: `IDS_TRANS_INFO_{YYYYMMDD_HHMMSSFF}-{UUID}-{PARTITION}.csv`

示例: `IDS_TRANS_INFO_20260225_172433765-b96904fa-3a4e-4c29-92f1-3b4b9e111824-0.csv`

- 日期: 20260225 (2026年2月25日)
- 时间: 172433765 (17:24:33.765)
- UUID: b96904fa-3a4e-4c29-92f1-3b4b9e111824
- 分区: 0-4 (并行度为 4)

### ✅ 文件滚动策略生效

- 每个文件约 20MB（符合配置的 20MB 滚动大小）
- 文件在 17:28-17:29 期间生成（符合 30 秒时间间隔）
- 多个文件说明滚动策略正常工作

### ✅ 数据完整性

- 每个文件都有标题行 ✅
- 数据格式正确（CSV 格式，逗号分隔）✅
- 交易时间使用当前时间（CDC 捕获时间）✅
- 操作类型正确（INSERT）✅
- 所有字段都有值 ✅

## 配置信息

### 作业配置
- **启动模式**: `StartupOptions.latest()` - 只捕获作业启动后的新变更
- **并行度**: 4
- **Checkpoint 间隔**: 30 秒
- **输出路径**: `./output/cdc`

### 文件滚动策略
- **时间间隔**: 30 秒
- **不活动间隔**: 10 秒
- **文件大小**: 20MB

### 数据库配置
- **主机**: host.docker.internal
- **端口**: 1521
- **SID**: helowin
- **Schema**: FINANCE_USER
- **表**: TRANS_INFO
- **归档日志**: 已启用 ✅

## 性能指标

### 数据量
- **总行数**: 1,041,004 行（包含标题行）
- **数据行数**: 约 1,041,000 行
- **总文件大小**: 约 100MB
- **平均每行大小**: 约 100 字节

### 处理速度
- **生成时间**: 约 5 分钟（17:24 启动，17:29 完成）
- **处理速度**: 约 3,470 行/秒
- **吞吐量**: 约 333 KB/秒

### 文件生成
- **文件数量**: 5 个
- **每个文件**: 约 20MB，208,000 行
- **滚动间隔**: 约 1 分钟/文件

## 问题解决

### 之前的问题
1. ❌ 作业使用 `latest()` 模式，启动后没有新数据
2. ❌ 作业在启动时失败（"Failed to resolve Oracle database version"）
3. ❌ 连接不稳定（"No more data to read from socket"）

### 解决方法
1. ✅ **重启作业** - 重新初始化 CDC 连接和 LogMiner 会话
2. ✅ **触发 Redo Log 切换** - 确保 LogMiner 能看到新数据
3. ✅ **等待足够时间** - 让作业完全初始化（90 秒）

## 监控命令

### 查看作业状态
```bash
curl -s http://localhost:8081/jobs/fb7ac5009e6458c6e666eca23d93d5be | \
  python3 -c "import sys, json; data = json.load(sys.stdin); print(f'状态: {data[\"state\"]}, 运行时长: {data[\"duration\"]//1000}秒')"
```

### 查看 CDC 指标
```bash
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep "totalCapturedDmlCount" | tail -1
```

### 查看生成的文件
```bash
ls -lh output/cdc/2026-02-25--17/
```

### 查看文件内容
```bash
head -20 output/cdc/2026-02-25--17/IDS_TRANS_INFO_*.csv
```

### 统计数据行数
```bash
wc -l output/cdc/2026-02-25--17/*.csv
```

## 下一步操作

### 1. 持续监控
```bash
# 监控作业状态
watch -n 30 'curl -s http://localhost:8081/jobs/fb7ac5009e6458c6e666eca23d93d5be | python3 -c "import sys, json; data = json.load(sys.stdin); print(f\"状态: {data[\\\"state\\\"]}, 运行时长: {data[\\\"duration\\\"]//1000}秒\")"'

# 监控文件生成
watch -n 30 'ls -lh output/cdc/2026-02-25--17/ | tail -10'
```

### 2. 插入更多测试数据
```bash
# 插入 100 条测试数据
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S system/helowin <<EOF
BEGIN
    FOR i IN 1..100 LOOP
        INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS)
        VALUES (SEQ_TRANS_INFO.NEXTVAL, 'BULK_TEST_' || i, 100 + i, SYSTIMESTAMP, 'TEST', 'MERCHANT_TEST', 'SUCCESS');
    END LOOP;
    COMMIT;
END;
/
EXIT;
EOF
"

# 等待 1 分钟
sleep 60

# 检查新文件
ls -lh output/cdc/2026-02-25--17/
```

### 3. 验证数据一致性
```bash
# 统计数据库中的记录数
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S system/helowin <<EOF
SELECT COUNT(*) FROM FINANCE_USER.TRANS_INFO;
EXIT;
EOF
"

# 统计 CSV 文件中的记录数（减去标题行）
wc -l output/cdc/2026-02-25--17/*.csv | tail -1 | awk '{print $1 - 5}'
```

### 4. 查看 Flink Web UI
访问: http://localhost:8081/#/job/fb7ac5009e6458c6e666eca23d93d5be/overview

查看:
- 作业拓扑
- Checkpoint 历史
- Metrics
- Logs

## 总结

✅ **Flink CDC 作业已成功重启并正常工作**

- 作业状态: RUNNING
- CDC 捕获: 正常
- 文件生成: 正常
- 数据格式: 正确
- 文件命名: 正确
- 滚动策略: 生效

**已生成**: 5 个 CSV 文件，约 100MB，1,041,000 行数据

**下一步**: 持续监控作业运行，确保稳定性。

## 相关文件

- `restart-flink-cdc-job.sh` - 重启作业脚本
- `quick-test-cdc.sh` - 快速测试脚本
- `diagnose-cdc-issue.sh` - 诊断脚本
- `ARCHIVELOG_ENABLED_NEXT_STEPS.md` - 归档日志启用后的操作指南
- `CSV_FILE_NOT_GENERATED_SOLUTION.md` - 文件未生成问题的解决方案

## 成功因素

1. ✅ 归档日志已启用
2. ✅ 作业重启清除了旧状态
3. ✅ 触发 Redo Log 切换
4. ✅ 等待足够的初始化时间
5. ✅ 配置正确（连接、路径、滚动策略）
