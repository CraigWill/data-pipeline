# Flink CDC 作业最终状态

## 作业信息

✅ **作业已成功启动并运行**

### 基本信息

```
Job ID: deb6c50413ab989fa1f850a168fe6d23
Job Name: Flink CDC 3.x Oracle Application
State: RUNNING
Start Time: 2026-02-25 13:21:01
Parallelism: 4
```

### 数据流拓扑

```
Source: Oracle CDC 3.x Source
  ↓
Application-Level Table Filter
  ↓
Map (JSON to CSV Converter)
  ↓
CSV File Sink: Writer
  ↓
CSV File Sink: Committer
```

**特点**：
- ✅ 单一 Source（Oracle CDC 3.x）
- ✅ 应用层表过滤
- ✅ JSON 到 CSV 转换
- ✅ 自动添加 CSV 标题行

## CSV 输出格式

### 标题行（第一行）

```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
```

### 数据行（从第二行开始）

```csv
2024-10-24 10:13:30,INSERT,20260225101309,ACC03163566,8447.97,1729760810000,DEPOSIT,MER008610,SUCCESS
2024-10-11 15:47:00,INSERT,20260225101310,ACC00319140,4903.38,1728640420000,PAY,MER002590,SUCCESS
```

### 字段说明

1. **交易时间** - 从 TRANS_TIME 转换的可读时间 (yyyy-MM-dd HH:mm:ss)
2. **操作类型** - INSERT, UPDATE, DELETE
3. **ID** - 交易ID
4. **ACCOUNT_ID** - 账户ID
5. **AMOUNT** - 交易金额
6. **TRANS_TIME** - 原始时间戳
7. **TRANS_TYPE** - 交易类型
8. **MERCHANT_ID** - 商户ID
9. **STATUS** - 交易状态

## 输出配置

### 文件位置

```
./output/cdc/
```

### 文件命名规则

- **写入中**: `.part-<uuid>-<index>.csv.inprogress.<uuid>`
- **已提交**: `part-<uuid>-<index>.csv`

### Rolling 策略

- **时间间隔**: 5 分钟
- **不活动时间**: 2 分钟
- **最大文件大小**: 128 MB

## 数据源配置

### Oracle 数据库

```
Host: host.docker.internal
Port: 1521
SID: helowin
Schema: FINANCE_USER
Table: TRANS_INFO
```

### CDC 模式

```
Startup Mode: latest (只捕获新变更)
Split Size: 8096
Parallelism: 4
```

## 监控和管理

### Web UI

```
Flink Dashboard: http://localhost:8081
Job Overview: http://localhost:8081/#/job/deb6c50413ab989fa1f850a168fe6d23/overview
```

### REST API

```bash
# 查看作业状态
curl http://localhost:8081/jobs/deb6c50413ab989fa1f850a168fe6d23

# 查看 Checkpoint 统计
curl http://localhost:8081/jobs/deb6c50413ab989fa1f850a168fe6d23/checkpoints

# 查看异常信息
curl http://localhost:8081/jobs/deb6c50413ab989fa1f850a168fe6d23/exceptions
```

### 日志查看

```bash
# 查看 TaskManager 日志
docker-compose logs -f taskmanager

# 查看 JobManager 日志
docker-compose logs -f jobmanager

# 查看所有日志
docker-compose logs -f
```

## 快速操作

### 启动作业

```bash
./start-flink-cdc-job.sh
```

### 停止作业

```bash
curl -X PATCH 'http://localhost:8081/jobs/deb6c50413ab989fa1f850a168fe6d23?mode=cancel'
```

### 查看输出文件

```bash
# 列出所有输出文件
ls -lRt output/cdc/ | head -20

# 查看最新文件内容
find output/cdc/ -type f -mmin -10 -exec head -20 {} \;

# 在容器内查看
docker exec realtime-pipeline-taskmanager-1 ls -lRt /opt/flink/output/cdc/
```

### 插入测试数据

由于使用了 `latest` 模式，需要插入新数据才能看到 CDC 事件：

```sql
-- 连接到 Oracle
sqlplus system/helowin@//host.docker.internal:1521/helowin

-- 插入测试数据
INSERT INTO FINANCE_USER.TRANS_INFO VALUES 
('TEST' || TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS'), 'ACC99999999', 1000.00, SYSTIMESTAMP, 'DEPOSIT', 'MER999999', 'SUCCESS');

COMMIT;
```

## 系统架构

### 组件列表

1. **Flink JobManager** - 作业调度和协调
2. **Flink TaskManager** - 任务执行（1个，4个 slots）
3. **Oracle Database** - 数据源（11g）
4. **Zookeeper** - Kafka 依赖（未用于 Flink HA）
5. **Kafka** - 备用组件（当前未使用）

### 网络配置

```
flink-network (bridge)
  ├── flink-jobmanager:8081 (Web UI)
  ├── flink-taskmanager
  ├── zookeeper:2181
  └── kafka:9092
```

## 故障排查

### 常见问题

#### 1. TaskManager 未注册

```bash
# 检查 TaskManager 状态
curl http://localhost:8081/overview | python3 -m json.tool

# 重启 TaskManager
docker-compose restart taskmanager
```

#### 2. JDBC 驱动问题

```bash
# 复制 JDBC 驱动
docker cp docker/debezium/ojdbc8.jar flink-jobmanager:/opt/flink/lib/
docker cp docker/debezium/ojdbc8.jar realtime-pipeline-taskmanager-1:/opt/flink/lib/

# 重启集群
docker-compose restart jobmanager taskmanager
```

#### 3. 作业失败

```bash
# 查看异常信息
curl http://localhost:8081/jobs/deb6c50413ab989fa1f850a168fe6d23/exceptions | python3 -m json.tool

# 查看日志
docker-compose logs taskmanager --tail=100 | grep -i error
```

## 性能指标

### Checkpoint 配置

```
Interval: 5 minutes (300000 ms)
Timeout: 10 minutes (600000 ms)
Min Pause: 1 minute (60000 ms)
Mode: EXACTLY_ONCE
```

### 资源配置

```
TaskManager Memory: 2GB
TaskManager Slots: 4
JobManager Memory: 1.6GB
Parallelism: 4
```

## 相关文档

- `NEW_CSV_FORMAT.md` - CSV 格式说明
- `SINGLE_SOURCE_FIX.md` - 单 Source 修复说明
- `TASKMANAGER_REGISTRATION_FIX.md` - TaskManager 注册问题解决
- `CSV_FILE_GENERATION_STATUS.md` - CSV 文件生成状态
- `README.md` - 项目总体说明

## 下一步

1. **验证数据捕获**
   - 插入测试数据
   - 检查 CSV 输出文件
   - 验证数据格式

2. **监控作业运行**
   - 查看 Checkpoint 统计
   - 监控资源使用
   - 检查错误日志

3. **生产环境准备**
   - 启用 HA 模式（可选）
   - 配置持久化存储
   - 设置监控告警

---

**日期**: 2026-02-25
**Job ID**: deb6c50413ab989fa1f850a168fe6d23
**状态**: ✅ 运行中
**Source 数量**: 1
**输出格式**: CSV（带标题行）
