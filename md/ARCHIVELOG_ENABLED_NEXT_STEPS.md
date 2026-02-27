# 归档日志已启用 - 下一步操作

## 当前状态

✅ **归档日志模式已启用**
```
Database log mode: Archive Mode
Automatic archival: Enabled
```

✅ **Flink CDC 作业正在运行**
- Job ID: 77fa5347dbd0e545fb3c7598b9f07d82
- 状态: RUNNING
- 运行时长: 80+ 分钟

❌ **问题：没有生成 CSV 文件**
- 作业使用 `latest()` 模式
- 作业启动后没有捕获到新数据
- 测试插入的 5 条数据也没有被捕获

## 问题分析

### 1. CDC 指标分析

最近的 CDC 指标（16:11:59）：
```
totalCapturedDmlCount=3113028  (这是旧作业的数据)
committedDmlCount=3200000      (这是旧作业的数据)
```

从 15:36:47 到 15:53:30 期间：
```
totalCapturedDmlCount=0
committedDmlCount=0
```

**结论**：当前作业（16:01:49 启动）没有捕获到任何新数据。

### 2. 可能的原因

1. **作业启动失败后重启**
   - 16:01:39: 失败（"Failed to resolve Oracle database version"）
   - 16:01:49: 重启成功
   - 使用 `latest()` 模式，从重启时的 SCN 开始

2. **数据库连接问题**
   - 16:11:59: "No more data to read from socket"
   - 连接不稳定导致 CDC 中断

3. **LogMiner 会话问题**
   - 可能需要更长的轮询间隔
   - 可能需要手动触发 redo log 切换

## 解决方案

### 方案 1：重启作业（推荐）

重启作业可以重新初始化 CDC 连接和 LogMiner 会话：

```bash
# 1. 取消当前作业
JOB_ID=77fa5347dbd0e545fb3c7598b9f07d82
curl -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel"

# 2. 等待作业取消
sleep 5

# 3. 重新提交作业
docker exec flink-jobmanager flink run -d \
  -c com.realtime.pipeline.FlinkCDC3App \
  /opt/flink/usrlib/realtime-data-pipeline.jar

# 4. 获取新的 Job ID
curl -s http://localhost:8081/jobs/overview | python3 -c "import sys, json; jobs = json.load(sys.stdin)['jobs']; [print(f'新作业 ID: {j[\"jid\"]}') for j in jobs if j['state'] == 'RUNNING']"
```

### 方案 2：切换到 initial() 模式读取历史数据

如果需要读取历史数据（包括之前插入的测试数据）：

```bash
# 1. 修改代码
# 编辑 src/main/java/com/realtime/pipeline/FlinkCDC3App.java
# 将 .startupOptions(StartupOptions.latest())
# 改为 .startupOptions(StartupOptions.initial())

# 2. 重新编译
mvn clean package -DskipTests

# 3. 取消当前作业
curl -X PATCH "http://localhost:8081/jobs/77fa5347dbd0e545fb3c7598b9f07d82?mode=cancel"

# 4. 更新 JAR
docker cp target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar \
  flink-jobmanager:/opt/flink/usrlib/realtime-data-pipeline.jar
docker cp target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar \
  realtime-pipeline-taskmanager-1:/opt/flink/usrlib/realtime-data-pipeline.jar

# 5. 提交新作业
docker exec flink-jobmanager flink run -d \
  -c com.realtime.pipeline.FlinkCDC3App \
  /opt/flink/usrlib/realtime-data-pipeline.jar
```

### 方案 3：手动触发 Redo Log 切换

有时 LogMiner 需要 redo log 切换才能看到新数据：

```bash
# 在 Oracle 容器中执行
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S / as sysdba <<EOF
ALTER SYSTEM SWITCH LOGFILE;
ALTER SYSTEM CHECKPOINT;
EXIT;
EOF
"

# 然后等待 1-2 分钟，检查是否捕获到数据
```

### 方案 4：插入更多测试数据并等待

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

# 触发 redo log 切换
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S / as sysdba <<EOF
ALTER SYSTEM SWITCH LOGFILE;
EXIT;
EOF
"

# 等待 2 分钟
sleep 120

# 检查文件
ls -lh output/cdc/
```

## 推荐执行顺序

1. **先尝试方案 3**（最简单，无需重启）
2. **如果方案 3 失败，尝试方案 1**（重启作业）
3. **如果需要历史数据，使用方案 2**（切换到 initial 模式）

## 验证步骤

### 1. 检查作业状态
```bash
curl -s http://localhost:8081/jobs/overview | python3 -c "import sys, json; jobs = json.load(sys.stdin)['jobs']; [print(f'ID: {j[\"jid\"]}, 状态: {j[\"state\"]}') for j in jobs if j['state'] == 'RUNNING']"
```

### 2. 监控 CDC 指标
```bash
# 每 30 秒检查一次
watch -n 30 'docker logs realtime-pipeline-taskmanager-1 2>&1 | grep "totalCapturedDmlCount" | tail -1'
```

### 3. 检查文件生成
```bash
# 宿主机
ls -lh output/cdc/

# 容器内
docker exec realtime-pipeline-taskmanager-1 ls -lh /opt/flink/output/cdc/
```

### 4. 查看文件内容
```bash
# 如果生成了文件
head -20 output/cdc/IDS_TRANS_INFO_*.csv
```

## 预期结果

成功后应该看到：
1. CDC 指标中 `totalCapturedDmlCount` 增加
2. `output/cdc/` 目录中生成 CSV 文件
3. 文件包含标题行和数据行
4. 文件命名格式：`IDS_TRANS_INFO_20260225_HHmmssSSS-uuid-0.csv`

## 故障排查

如果仍然没有生成文件：

1. **检查作业日志**
   ```bash
   docker logs realtime-pipeline-taskmanager-1 2>&1 | tail -100
   ```

2. **检查异常**
   ```bash
   curl -s http://localhost:8081/jobs/<JOB_ID>/exceptions
   ```

3. **查看 Flink Web UI**
   - 访问 http://localhost:8081
   - 查看作业的 Metrics 和 Logs

4. **检查数据库连接**
   ```bash
   docker exec realtime-pipeline-taskmanager-1 nc -zv host.docker.internal 1521
   ```

## 相关文件

- `CSV_FILE_NOT_GENERATED_SOLUTION.md` - 详细问题分析
- `ENABLE_ARCHIVELOG_GUIDE.md` - 归档日志启用指南
- `quick-test-cdc.sh` - 快速测试脚本
- `diagnose-cdc-issue.sh` - 诊断脚本
- `check-archivelog-docker.sh` - 检查归档日志状态

## 下一步

请选择一个方案执行，我建议：

**立即执行方案 3（最简单）**：
```bash
# 1. 插入测试数据
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S system/helowin <<EOF
INSERT INTO FINANCE_USER.TRANS_INFO VALUES (SEQ_TRANS_INFO.NEXTVAL, 'FINAL_TEST', 999.99, SYSTIMESTAMP, 'TEST', 'MERCHANT', 'SUCCESS');
COMMIT;
EXIT;
EOF
"

# 2. 触发 redo log 切换
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S / as sysdba <<EOF
ALTER SYSTEM SWITCH LOGFILE;
EXIT;
EOF
"

# 3. 等待 2 分钟
echo "等待 2 分钟..."
sleep 120

# 4. 检查结果
ls -lh output/cdc/
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep "totalCapturedDmlCount" | tail -1
```

如果方案 3 不起作用，我们再尝试方案 1（重启作业）。
