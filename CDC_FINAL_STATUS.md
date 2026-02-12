# Oracle CDC 最终状态报告

## 当前状态

### 1. 系统配置 ✅
- **Oracle 数据库**: 已配置并运行
  - 归档日志模式: ARCHIVELOG ✅
  - 补充日志: 已启用 ✅
  - 主机: host.docker.internal:1521
  - SID: helowin
  - 用户: system / helowin

- **Flink 集群**: 已部署并运行
  - JobManager: http://localhost:8081 ✅
  - TaskManager: 1 个实例，4 个任务槽 ✅

- **Kafka**: 已部署并运行
  - Broker: localhost:9092 ✅
  - Kafka UI: http://localhost:8080 ✅

### 2. Oracle CDC 应用程序 ✅
- **应用程序**: `OracleCDCApp.java`
- **CDC 方式**: LogMiner (基于归档日志)
- **监控表**: `FINANCE_USER.TRANS_INFO`
- **输出格式**: CSV 格式
  - 格式: `timestamp,table,operation,json_data`
  - 操作类型: INSERT, UPDATE, DELETE, READ
- **输出路径**: `/opt/flink/output/cdc/` (容器内) → `./output/cdc/` (宿主机)

### 3. 当前作业状态
- **作业 ID**: 79a35a12752a520e7bd192c3b5b9d2ea
- **作业名称**: Oracle CDC Application (LogMiner)
- **状态**: RUNNING ✅
- **当前阶段**: Snapshot (初始快照)

## 问题分析

### 为什么没有生成 CSV 文件？

1. **Snapshot 阶段耗时长**
   - Oracle CDC 首次启动时需要进行初始快照（Snapshot）
   - 快照阶段会捕获所有表的当前状态
   - 只有在快照完成后才会进入 Streaming 阶段并开始生成输出文件

2. **监控范围过大**
   - 之前的配置监控了整个 `HELOWIN` 数据库的所有表
   - 数据库包含数百个系统表（APEX_030200, SYSMAN 等）
   - 导致快照阶段需要很长时间

3. **配置已优化**
   - 已更新 `.env` 文件，只监控 `FINANCE_USER.TRANS_INFO` 表
   - 已更新 `OracleCDCApp.java`，添加了详细日志
   - 已重新构建应用程序

## 下一步操作

### 方案 1: 重新部署优化后的应用（推荐）

```bash
# 1. 停止当前作业
curl -X PATCH http://localhost:8081/jobs/79a35a12752a520e7bd192c3b5b9d2ea?mode=cancel

# 2. 重新构建 Docker 镜像
bash docker/build-images.sh

# 3. 重启服务
docker-compose down && docker-compose up -d

# 4. 等待服务启动（约 20 秒）
sleep 20

# 5. 提交新的 CDC 作业
docker exec flink-jobmanager flink run -d -c com.realtime.pipeline.OracleCDCApp /opt/flink/usrlib/realtime-data-pipeline.jar

# 6. 插入测试数据
bash test-oracle-cdc.sh

# 7. 检查输出文件
find output/cdc -type f -name "part-*"
```

### 方案 2: 等待当前作业完成快照

```bash
# 1. 监控作业日志
docker logs -f realtime-pipeline-taskmanager-1 | grep -E "(snapshot|streaming|INSERT|UPDATE)"

# 2. 等待看到 "Snapshot completed" 或 "Streaming started" 消息

# 3. 插入测试数据触发 CDC 事件
bash test-oracle-cdc.sh

# 4. 检查输出文件
find output/cdc -type f -name "part-*"
```

## 配置文件更新

### `.env` 文件
```bash
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_USERNAME=system
DATABASE_PASSWORD=helowin
DATABASE_SID=helowin
DATABASE_SCHEMA=FINANCE_USER
DATABASE_TABLES=TRANS_INFO
OUTPUT_PATH=/opt/flink/output/cdc
```

### `docker-compose.yml`
- 已添加输出目录挂载: `./output:/opt/flink/output`

### `OracleCDCApp.java`
- 已优化表监控配置
- 已添加详细日志
- 已改进 CSV 输出格式

## 测试步骤

### 1. 插入测试数据
```bash
bash test-oracle-cdc.sh
```

### 2. 检查输出文件
```bash
# 列出所有输出文件
find output/cdc -type f -name "part-*"

# 查看文件内容
cat output/cdc/*/part-*
```

### 3. 预期输出格式
```csv
1770884833471,TRANS_INFO,INSERT,"{""before"":null,""after"":{""TRANS_ID"":1,""TRANS_TYPE"":""DEPOSIT"",""AMOUNT"":1000.00,...},""op"":""c"",""ts_ms"":1770884833471}"
```

## 故障排查

### 检查作业状态
```bash
curl -s http://localhost:8081/jobs/79a35a12752a520e7bd192c3b5b9d2ea | python3 -m json.tool
```

### 检查作业日志
```bash
docker logs realtime-pipeline-taskmanager-1 --tail 100
```

### 检查 Oracle 连接
```bash
docker exec oracle11g sqlplus system/helowin@helowin <<EOF
SELECT LOG_MODE FROM V\$DATABASE;
SELECT SUPPLEMENTAL_LOG_DATA_MIN FROM V\$DATABASE;
SELECT COUNT(*) FROM FINANCE_USER.TRANS_INFO;
EXIT;
EOF
```

### 检查输出目录权限
```bash
ls -lah output/cdc/
docker exec flink-jobmanager ls -lah /opt/flink/output/cdc/
```

## 总结

1. ✅ Oracle 数据库已正确配置（归档日志 + 补充日志）
2. ✅ Flink 集群已部署并运行
3. ✅ Oracle CDC 作业已提交并运行
4. ⏳ 作业正在进行初始快照，完成后将开始生成 CSV 文件
5. ✅ 配置已优化，只监控 `FINANCE_USER.TRANS_INFO` 表
6. ✅ 输出目录已正确挂载到宿主机

**建议**: 使用方案 1 重新部署优化后的应用，这样可以更快地完成快照并开始生成输出文件。
