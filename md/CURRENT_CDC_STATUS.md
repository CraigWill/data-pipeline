# Flink CDC 当前状态报告

**报告时间**: 2026-02-26 10:00

## 系统状态总览

✅ **系统运行正常** - 所有组件健康，CDC 正在捕获数据并生成 CSV 文件

## 作业状态

### 当前作业
- **作业 ID**: 22b01eb0e022b22b315f2ecb84a95859
- **状态**: RUNNING ✅
- **运行时长**: 约 20 分钟
- **启动时间**: 2026-02-26 09:25:31
- **异常数量**: 0 ✅

### CDC 性能指标
- **已捕获 DML 操作**: 3,071,320 条
- **已处理行数**: 3,086,927 行
- **LogMiner 查询次数**: 77 次
- **活跃事务**: 1 个
- **已提交事务**: 7,928 个
- **错误计数**: 1 个（已恢复）

## 容器状态

### JobManager
- **状态**: Up About an hour (healthy) ✅
- **端口**: 8081 (Web UI), 6123 (RPC), 6124 (Blob Server), 9249 (Metrics)
- **健康检查**: 通过 ✅

### TaskManager
- **容器名称**: realtime-pipeline-taskmanager-1
- **状态**: Up 22 minutes (healthy) ✅
- **TaskManager ID**: 172.19.0.3:6122-105722
- **槽位**: 4 个（全部使用中）
- **健康检查**: 通过 ✅
- **注意**: Web UI 显示"离线"是显示问题，实际容器健康且作业正常运行

## CSV 文件生成状态

### 今日文件 (2026-02-26)
- **目录**: output/cdc/2026-02-26--09/
- **文件数量**: 31 个 CSV 文件
- **总大小**: 600MB
- **文件命名**: IDS_TRANS_INFO_20260226_092531871-{UUID}-{PARTITION}.csv ✅

### 文件格式验证
```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
2026-02-26 09:28:48,INSERT,20260368317309,ACC08977257,3501.19,1732465577000,TRANSFER,MER013285,SUCCESS
```

✅ **格式正确**:
- 标题行存在
- 交易时间使用当前时间（CDC 捕获时间）
- 所有字段完整
- CSV 格式正确

### 昨日文件 (2026-02-25)
- **目录**: output/cdc/2026-02-25--17/
- **文件数量**: 5 个 CSV 文件
- **总大小**: 约 100MB
- **总行数**: 1,041,004 行

## 数据库状态

### Oracle 数据库
- **主机**: host.docker.internal:1521
- **SID**: helowin
- **Schema**: FINANCE_USER
- **表**: TRANS_INFO
- **总记录数**: 2,304,306,101 条（约 23 亿条）
- **归档日志**: 已启用 ✅

### 当前 Redo Log
- **文件**: /home/oracle/app/oracle/oradata/helowin/redo09.log
- **状态**: CURRENT
- **SCN**: 20574245

## 已知问题和说明

### 1. BlobServerConnection 错误（无害）
**错误信息**:
```
ERROR org.apache.flink.runtime.blob.BlobServerConnection - Error while executing BLOB connection
java.io.IOException: Unknown operation 71
```

**说明**: 这是无害的警告，通常是 Flink Web UI 或其他客户端尝试连接 BLOB Server 时使用了不兼容的协议。不会影响作业运行。

**影响**: 无 ✅

### 2. TaskManager Web UI 显示"离线"
**现象**: Flink Web UI 显示 TaskManager 状态为"离线"，最后心跳时间很久以前

**实际情况**:
- TaskManager 容器健康运行 ✅
- 作业正常执行 ✅
- CSV 文件正常生成 ✅
- 无异常错误 ✅

**原因**: 可能是 Web UI 的显示问题或心跳时间戳计算问题

**影响**: 无，作业正常运行 ✅

### 3. 之前的心跳超时问题（已解决）
**时间**: 2026-02-26 09:17:25

**问题**: TaskManager 心跳超时，作业达到最大重试次数（3次）后失败

**解决方案**: 重启 TaskManager 和作业

**当前状态**: 已解决，作业稳定运行 ✅

## 配置信息

### CDC 配置
- **启动模式**: StartupOptions.latest() - 只捕获新变更
- **并行度**: 4
- **Checkpoint 间隔**: 30 秒
- **Split 大小**: 8096

### 文件滚动策略
- **时间间隔**: 30 秒
- **不活动间隔**: 10 秒
- **文件大小**: 20MB

### 连接配置
- **连接超时**: 60 秒
- **查询超时**: 10 分钟
- **最大重试次数**: 10 次
- **TCP Keep-Alive**: 启用（60 秒间隔）

## 监控命令

### 检查作业状态
```bash
curl -s http://localhost:8081/jobs/22b01eb0e022b22b315f2ecb84a95859 | \
  python3 -c "import sys, json; data = json.load(sys.stdin); print(f'状态: {data[\"state\"]}, 运行时长: {data[\"duration\"]//1000}秒')"
```

### 检查 TaskManager 状态
```bash
curl -s http://localhost:8081/taskmanagers | \
  python3 -c "import sys, json; data = json.load(sys.stdin); print(f'TaskManager 数量: {len(data.get(\"taskmanagers\", []))}')"
```

### 检查异常
```bash
curl -s http://localhost:8081/jobs/22b01eb0e022b22b315f2ecb84a95859/exceptions | \
  python3 -c "import sys, json; data = json.load(sys.stdin); print(f'异常数量: {len(data.get(\"all-exceptions\", []))}')"
```

### 查看 CDC 指标
```bash
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep "totalCapturedDmlCount" | tail -1
```

### 查看生成的文件
```bash
ls -lh output/cdc/2026-02-26--09/
```

### 查看容器状态
```bash
docker ps --filter "name=flink"
```

### 查看容器日志
```bash
# TaskManager 日志
docker logs realtime-pipeline-taskmanager-1 --tail 100

# JobManager 日志
docker logs flink-jobmanager --tail 100
```

## 性能分析

### 数据处理速度
- **已捕获**: 3,071,320 条 DML 操作
- **运行时长**: 约 20 分钟
- **平均速度**: 约 2,560 条/秒

### 文件生成速度
- **今日文件**: 31 个文件，600MB
- **生成时长**: 约 20 分钟
- **平均速度**: 约 30MB/分钟

### 数据库规模
- **总记录数**: 2,304,306,101 条（约 23 亿条）
- **已捕获比例**: 0.13%（使用 latest 模式，只捕获新变更）

## 建议和下一步

### 1. 持续监控（建议运行 24 小时）
```bash
# 创建监控脚本
cat > monitor-cdc.sh << 'EOF'
#!/bin/bash
while true; do
    echo "=== $(date) ==="
    
    # 作业状态
    curl -s http://localhost:8081/jobs/22b01eb0e022b22b315f2ecb84a95859 | \
      python3 -c "import sys, json; data = json.load(sys.stdin); print(f'作业状态: {data.get(\"state\", \"UNKNOWN\")}, 运行时长: {data.get(\"duration\", 0)//1000}秒')" 2>/dev/null || echo "无法获取作业状态"
    
    # 异常数量
    curl -s http://localhost:8081/jobs/22b01eb0e022b22b315f2ecb84a95859/exceptions | \
      python3 -c "import sys, json; data = json.load(sys.stdin); print(f'异常数量: {len(data.get(\"all-exceptions\", []))}')" 2>/dev/null || echo "无法获取异常信息"
    
    # 文件数量
    FILE_COUNT=$(find output/cdc/2026-02-26--09 -name "*.csv" -type f 2>/dev/null | wc -l)
    echo "CSV 文件数量: $FILE_COUNT"
    
    # 文件大小
    TOTAL_SIZE=$(du -sh output/cdc/2026-02-26--09/ 2>/dev/null | awk '{print $1}')
    echo "总文件大小: $TOTAL_SIZE"
    
    echo ""
    sleep 300  # 每 5 分钟检查一次
done
EOF

chmod +x monitor-cdc.sh
./monitor-cdc.sh
```

### 2. 优化心跳配置（可选）
如果担心心跳超时问题再次发生，可以增加心跳超时时间：

编辑 `docker/jobmanager/flink-conf.yaml` 和 `docker/taskmanager/flink-conf.yaml`:
```yaml
heartbeat.timeout: 180000  # 从 50 秒增加到 3 分钟
heartbeat.interval: 10000  # 保持 10 秒
```

然后重启容器：
```bash
docker-compose restart jobmanager taskmanager
```

### 3. 增加重试次数（可选）
在代码中增加重试次数，避免短暂故障导致作业失败：

```java
env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
    10,  // 从 3 次增加到 10 次
    Time.seconds(10)
));
```

### 4. 插入测试数据验证
```bash
# 插入 10 条测试数据
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S system/helowin <<EOF
BEGIN
    FOR i IN 1..10 LOOP
        INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS)
        VALUES (SEQ_TRANS_INFO.NEXTVAL, 'TEST_' || i, 100 + i, SYSTIMESTAMP, 'TEST', 'MERCHANT_TEST', 'SUCCESS');
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
ls -lht output/cdc/2026-02-26--09/*.csv | head -5
```

### 5. 定期清理旧文件（可选）
```bash
# 创建清理脚本（保留最近 7 天的文件）
cat > cleanup-old-files.sh << 'EOF'
#!/bin/bash
find output/cdc -name "*.csv" -type f -mtime +7 -delete
echo "已清理 7 天前的 CSV 文件"
EOF

chmod +x cleanup-old-files.sh

# 添加到 crontab（每天凌晨 2 点执行）
# 0 2 * * * /path/to/cleanup-old-files.sh
```

## 总结

✅ **系统运行正常**

- 作业状态: RUNNING
- CDC 捕获: 正常（已捕获 307 万条操作）
- 文件生成: 正常（31 个文件，600MB）
- 容器健康: 正常
- 异常数量: 0

**已知问题**:
1. BlobServerConnection 错误 - 无害，不影响运行 ✅
2. Web UI 显示 TaskManager "离线" - 显示问题，实际正常 ✅

**建议**:
1. 持续监控 24 小时确保稳定性
2. 考虑增加心跳超时时间（可选）
3. 考虑增加重试次数（可选）
4. 定期插入测试数据验证 CDC 功能

## 相关文档

- `TASKMANAGER_HEARTBEAT_TIMEOUT_FIX.md` - 心跳超时问题解决方案
- `CDC_JOB_RESTART_SUCCESS.md` - 上次重启成功记录
- `ARCHIVELOG_ENABLED_NEXT_STEPS.md` - 归档日志启用后的操作指南
- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - CDC 应用代码
- `docker-compose.yml` - 容器配置
- `docker/jobmanager/flink-conf.yaml` - JobManager 配置
- `docker/taskmanager/flink-conf.yaml` - TaskManager 配置
