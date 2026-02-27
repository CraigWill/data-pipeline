# Flink CDC 3.x 作业已启动 ✅

## 作业信息

**Job ID**: `3b34345b0b9754fad3d2338f7ebee0b2`  
**状态**: ✅ RUNNING  
**作业名称**: Flink CDC 3.x Oracle Application  
**启动时间**: 2026-02-25 10:12:40  

## 访问地址

- **Flink Web UI**: http://localhost:8081/#/job/3b34345b0b9754fad3d2338f7ebee0b2/overview
- **Kafka UI**: http://localhost:8082
- **Flink 概览**: http://localhost:8081

## 作业配置

| 配置项 | 值 |
|--------|-----|
| Flink CDC 版本 | 3.2.1 |
| 启动模式 | Latest (只捕获新变更) |
| 并行度 | 4 |
| Checkpoint 间隔 | 3 分钟 |
| 数据源 | Oracle 11g (FINANCE_USER.TRANS_INFO) |
| 输出格式 | CSV |
| 输出路径 | ./output/cdc/ |

## 验证结果

### ✅ 作业运行正常
- Flink 集群: 1 个 TaskManager, 4 个任务槽
- 作业状态: RUNNING
- 无错误日志

### ✅ CDC 数据捕获成功
测试插入记录已成功捕获:
```csv
"2026-02-25 10:13:13","TRANS_INFO","INSERT","{'ID':'20260225101308','ACCOUNT_ID':'TEST_JOB','AMOUNT':'66666.66',...}"
```

### ✅ 输出文件生成
- 输出目录: `output/cdc/2026-02-25--10/`
- 文件数量: 9 个
- 最新文件大小: 378B
- 文件格式: CSV (in-progress 和 part 文件)

### ✅ Oracle 连接正常
- 数据库版本: Oracle 11g Enterprise Edition
- 表记录数: 2,157,090,101 (21.5亿+)
- LogMiner: 已启用
- 归档日志: ARCHIVELOG 模式

## 管理脚本

### 启动作业
```bash
./start-flink-cdc.sh
```

### 检查状态
```bash
./check-cdc-status.sh
```

### 查看输出
```bash
ls -lht output/cdc/
tail -f output/cdc/2026-02-25--10/*.inprogress.*
```

### 查看日志
```bash
# JobManager 日志
docker logs flink-jobmanager --tail 50

# TaskManager 日志
docker logs realtime-pipeline-taskmanager-1 --tail 50
```

### 停止作业
```bash
# 获取 Job ID
curl -s http://localhost:8081/jobs/overview | python3 -m json.tool

# 取消作业
docker exec flink-jobmanager flink cancel <JOB_ID>
```

## 监控指标

### 通过 Web UI 查看
访问 http://localhost:8081/#/job/3b34345b0b9754fad3d2338f7ebee0b2/overview

### 通过 REST API 查看
```bash
# 作业概览
curl -s http://localhost:8081/jobs/3b34345b0b9754fad3d2338f7ebee0b2 | python3 -m json.tool

# 作业指标
curl -s http://localhost:8081/jobs/3b34345b0b9754fad3d2338f7ebee0b2/metrics

# 输出记录数
curl -s "http://localhost:8081/jobs/3b34345b0b9754fad3d2338f7ebee0b2/vertices/<VERTEX_ID>/metrics?get=numRecordsOut"
```

## 测试 CDC 功能

### 插入测试数据
```bash
docker exec oracle11g bash -c "source /home/oracle/.bash_profile && sqlplus -s system/helowin@helowin <<EOF
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS) 
VALUES (TO_NUMBER(TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS')), 'TEST_CDC', 99999.99, SYSDATE, 'PAYMENT', 'MERCHANT_Z', 'SUCCESS');
COMMIT;
EXIT;
EOF"
```

### 查看捕获结果
```bash
# 等待几秒让 CDC 处理
sleep 5

# 查看最新输出
find output/cdc -type f -name "*.inprogress.*" -exec tail -5 {} \;
```

## 故障排查

### 作业未运行
```bash
# 检查 Flink 集群
docker-compose ps

# 重启 Flink
docker-compose restart jobmanager taskmanager

# 重新提交作业
./start-flink-cdc.sh
```

### 无输出文件
```bash
# 检查作业状态
curl -s http://localhost:8081/jobs/3b34345b0b9754fad3d2338f7ebee0b2 | python3 -m json.tool | grep state

# 查看 TaskManager 日志
docker logs realtime-pipeline-taskmanager-1 --tail 100 | grep -E "ERROR|Exception"

# 插入测试数据验证
# (见上面的测试 CDC 功能部分)
```

### JDBC 驱动错误
```bash
# 检查驱动是否在 lib 目录
docker exec flink-jobmanager ls -lh /opt/flink/lib/ | grep ojdbc

# 如果没有，重新复制
docker cp /tmp/ojdbc8.jar flink-jobmanager:/opt/flink/lib/
docker cp /tmp/ojdbc8.jar realtime-pipeline-taskmanager-1:/opt/flink/lib/

# 重启容器
docker-compose restart jobmanager taskmanager
```

## 相关文档

- [系统组件清单](SYSTEM_COMPONENTS.md)
- [Flink CDC 3.x 配置](FLINK_CDC3_LATEST_MODE_SUCCESS.md)
- [JDBC 驱动修复](JDBC_DRIVER_FIX_SUCCESS.md)
- [Kafka 连接修复](KAFKA_CONNECTION_FIX.md)
- [当前 CDC 状态](CURRENT_CDC_STATUS.md)

## 总结

Flink CDC 3.x 作业已成功启动并运行，正在实时捕获 Oracle 数据库的变更数据。系统运行稳定，所有组件状态正常。
