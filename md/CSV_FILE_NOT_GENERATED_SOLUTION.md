# CSV 文件未生成问题 - 解决方案

## 问题描述

Flink CDC 作业运行正常（状态：RUNNING），CDC 捕获了 3,113,028 条 DML 操作，但 `output/cdc` 目录为空，没有生成任何 CSV 文件。

## 根本原因分析

### 1. 作业启动模式
- 使用 `StartupOptions.latest()` 模式
- 只捕获作业启动**之后**的新变更
- 不读取历史数据（跳过 snapshot）

### 2. 作业启动历史
根据日志分析：
- **15:53:30**: 作业因 "ORA-01325: archive log mode must be enabled" 失败
- **16:01:39**: 作业重启，但因 "Failed to resolve Oracle database version" 再次失败
- **16:01:49**: 作业第三次启动，成功运行
- **16:01:49 之后**: 数据库没有新的变更，所以没有数据流入 FileSink

### 3. CDC 指标说明
- `totalCapturedDmlCount=3,113,028`: 这是**之前作业**捕获的数据
- `committedDmlCount=3,200,000`: 这是**之前作业**提交的数据
- 当前作业（16:01:49 启动）没有捕获到新数据

### 4. 文件生成条件
FileSink 只有在以下情况下才会生成文件：
1. 有数据流入 Sink
2. 满足滚动策略（30秒时间间隔 或 20MB 大小 或 10秒不活动）
3. Checkpoint 完成

**当前情况**：没有数据流入 → 没有文件生成

## 解决方案

### 方案 1：插入新数据测试（推荐）

插入新数据到数据库，验证当前作业是否能捕获并生成文件：

```bash
# 1. 插入测试数据
sqlplus64 system/helowin@localhost:1521/helowin <<EOF
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS)
VALUES (SEQ_TRANS_INFO.NEXTVAL, 'TEST_001', 100.50, SYSTIMESTAMP, 'TEST', 'MERCHANT_TEST', 'SUCCESS');
COMMIT;
EXIT;
EOF

# 2. 等待 40 秒（超过 30 秒滚动间隔）
sleep 40

# 3. 检查文件
ls -lh output/cdc/
docker exec realtime-pipeline-taskmanager-1 ls -lh /opt/flink/output/cdc/
```

### 方案 2：切换到 initial() 模式读取历史数据

如果需要读取历史数据，修改代码：

```java
// 修改 FlinkCDC3App.java
.startupOptions(StartupOptions.initial()) // 读取历史数据 + 新变更
```

然后重新编译、部署、启动作业：

```bash
# 1. 取消当前作业
JOB_ID=$(curl -s http://localhost:8081/jobs/overview | python3 -c "import sys, json; jobs = json.load(sys.stdin)['jobs']; print([j['jid'] for j in jobs if j['state'] == 'RUNNING'][0])")
curl -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel"

# 2. 重新编译
mvn clean package -DskipTests

# 3. 更新 JAR
docker cp target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar flink-jobmanager:/opt/flink/usrlib/realtime-data-pipeline.jar
docker cp target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar realtime-pipeline-taskmanager-1:/opt/flink/usrlib/realtime-data-pipeline.jar

# 4. 提交新作业
docker exec flink-jobmanager flink run -d -c com.realtime.pipeline.FlinkCDC3App /opt/flink/usrlib/realtime-data-pipeline.jar
```

### 方案 3：使用 timestamp() 模式从指定时间点开始

从特定时间点开始读取：

```java
// 从 1 小时前开始读取
.startupOptions(StartupOptions.timestamp(System.currentTimeMillis() - 3600000))
```

## 验证步骤

### 1. 检查作业状态
```bash
curl -s http://localhost:8081/jobs/overview | python3 -c "import sys, json; jobs = json.load(sys.stdin)['jobs']; [print(f'ID: {j[\"jid\"]}, 状态: {j[\"state\"]}, 名称: {j[\"name\"]}') for j in jobs if j['state'] == 'RUNNING']"
```

### 2. 监控 CDC 指标
```bash
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep "totalCapturedDmlCount" | tail -1
```

### 3. 检查文件生成
```bash
# 宿主机
ls -lh output/cdc/

# 容器内
docker exec realtime-pipeline-taskmanager-1 ls -lh /opt/flink/output/cdc/

# 搜索所有文件
docker exec realtime-pipeline-taskmanager-1 find /opt/flink/output -type f \( -name "*.csv" -o -name "*.inprogress" \)
```

### 4. 查看文件内容（如果生成了）
```bash
# 查看最新的 CSV 文件
docker exec realtime-pipeline-taskmanager-1 find /opt/flink/output/cdc -name "*.csv" -exec head -20 {} \;
```

## 预期结果

### 方案 1（插入新数据）
- 插入数据后 30-40 秒内应该生成 CSV 文件
- 文件命名格式：`IDS_TRANS_INFO_20260225_HHmmssSSS-uuid-0.csv`
- 文件包含标题行和数据行

### 方案 2（initial 模式）
- 作业启动后会先读取历史数据（snapshot）
- 然后切换到增量模式（redo log）
- 应该生成包含历史数据的 CSV 文件

## 常见问题

### Q1: 为什么之前的指标显示捕获了 300 万条数据？
A: 那是之前失败的作业捕获的数据。当前作业（16:01:49 启动）是新的实例，从 latest 位置开始，没有捕获到新数据。

### Q2: latest() 模式适合什么场景？
A: 适合以下场景：
- 只关心实时新数据
- 历史数据已经处理过
- 避免重复处理数据

### Q3: 如何确认数据流是否到达 FileSink？
A: 查看 Flink Web UI 的 Metrics：
1. 访问 http://localhost:8081
2. 进入作业详情
3. 查看 "CSV File Sink" 的 numRecordsIn 指标

### Q4: 文件路径配置正确吗？
A: 是的，路径配置正确：
- 代码中：`./output/cdc`（相对路径）
- 容器内：`/opt/flink/output/cdc`（绝对路径）
- Volume 映射：`./output:/opt/flink/output`

## 下一步行动

**推荐执行方案 1**：

```bash
# 运行测试脚本
chmod +x test-cdc-with-new-data.sh
./test-cdc-with-new-data.sh
```

如果测试成功生成文件，说明作业配置正确，只是缺少新数据。
如果测试失败，需要进一步调查 FileSink 配置或数据流问题。

## 相关文件

- `FlinkCDC3App.java` - 主应用程序
- `docker-compose.yml` - Docker 配置
- `test-cdc-with-new-data.sh` - 测试脚本
- `diagnose-cdc-issue.sh` - 诊断脚本

## 时间线

- **15:53:30**: 作业失败（archive log 未启用）
- **16:01:39**: 作业失败（无法解析数据库版本）
- **16:01:49**: 作业成功启动（latest 模式）
- **16:01:49 - 现在**: 没有新数据 → 没有文件生成

## 结论

问题不是代码或配置错误，而是：
1. 使用 `latest()` 模式
2. 作业启动后数据库没有新变更
3. 没有数据流入 FileSink
4. 因此没有生成文件

**解决方法**：插入新数据或切换到 `initial()` 模式读取历史数据。
