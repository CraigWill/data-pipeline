# Flink CDC 实现状态

## 当前状态: ⚠️ Flink CDC 3.x 运行但无输出

Flink CDC 3.x 作业已成功提交并运行，但未产生任何 CDC 事件输出。

## 问题描述

### 当前情况

Flink CDC 3.x 作业已成功提交并运行：
- ✅ 作业状态: RUNNING
- ✅ 无异常或错误
- ⚠️ 输出记录数: 0
- ⚠️ 未生成新的 CSV 文件

### 根本原因

**大表 Snapshot 问题**：
- TRANS_INFO 表有 **2,152,090,100** 条记录（21亿+）
- Flink CDC 3.x 默认从 `StartupOptions.initial()` 开始，需要先完成 snapshot
- Snapshot 阶段需要扫描所有历史数据，对于如此大的表可能需要数小时甚至数天
- 当前作业卡在 snapshot 阶段，还未进入 streaming 阶段

## 已尝试的解决方案

### 方案 1: Flink CDC 2.4.2 ❌
- **问题**: Debezium 类加载错误
- **详情**: 见之前的状态文档

### 方案 2: Flink CDC 3.2.1 (当前) ⚠️
- **状态**: 作业运行中，但卡在 snapshot 阶段
- **实现**: `src/main/java/com/realtime/pipeline/FlinkCDC3App.java`
- **问题**: 表太大（21亿+记录），snapshot 耗时极长
- **文件**: 
  - 应用代码: `src/main/java/com/realtime/pipeline/FlinkCDC3App.java`
  - 提交脚本: `submit-flink-cdc-job.sh`
  - 状态检查: `check-flink-cdc3-status.sh`

### 方案 3: Standalone JDBC CDC ✅
- **状态**: 工作正常（基于轮询）
- **实现**: `src/main/java/com/realtime/pipeline/StandaloneCDCApp.java`
- **详情**: 见 `CDC_IMPLEMENTATION_SUCCESS.md`

## 技术分析

### Flink CDC Oracle Connector 的限制

1. **依赖复杂性**:
   ```xml
   flink-connector-oracle-cdc (2.4.2)
   ├── debezium-connector-oracle (2.4.2.Final)
   ├── debezium-core (2.4.2.Final)
   ├── debezium-api (2.4.2.Final)
   └── infinispan (14.0.11.Final)
   ```

2. **类加载问题**:
   - Flink 使用 ChildFirstClassLoader
   - Debezium 的某些内部类无法被正确加载
   - 特别是 `io.debezium.relational.history.*` 包下的类

3. **版本兼容性**:
   - Flink 1.18.0
   - Flink CDC 2.4.2
   - Debezium 2.4.2.Final
   - Oracle 11g

### 为什么 Standalone 方案可行

1. **简单依赖**: 只需要 Oracle JDBC driver
2. **无类加载冲突**: 直接在应用类加载器中运行
3. **易于调试**: 标准 Java 应用，日志清晰
4. **灵活性高**: 可以自定义任何逻辑

## 推荐方案

### 立即可用方案: 修改 Flink CDC 3.x 启动模式 ✅

**问题**: 当前使用 `StartupOptions.initial()` 需要扫描 21 亿条历史记录

**解决方案**: 修改为 `StartupOptions.latest()` 跳过 snapshot，只捕获新的变更

**优点**:
- 立即开始捕获新的数据变更
- 无需等待漫长的 snapshot 阶段
- 适合只关心增量变更的场景

**缺点**:
- 不会捕获历史数据
- 只能获取修改后的新增/更新/删除事件

**实施步骤**:
```bash
# 1. 取消当前作业
curl -X PATCH http://localhost:8081/jobs/6868437613383535578929d55fc63b77?mode=cancel

# 2. 修改 FlinkCDC3App.java 中的启动选项
# 将 .startupOptions(StartupOptions.initial())
# 改为 .startupOptions(StartupOptions.latest())

# 3. 重新编译和提交
mvn clean package -DskipTests
./submit-flink-cdc-job.sh
```

### 短期方案: Standalone JDBC CDC ✅

**优点**:
- 已经工作并生成 CSV 文件
- 简单可靠，易于维护
- 无复杂依赖问题
- 可以增量读取（使用时间戳或 ID 过滤）

**缺点**:
- 基于轮询，不是真正的 CDC
- 延迟取决于轮询间隔
- 只能检测 INSERT（需要额外逻辑支持 UPDATE/DELETE）

**使用方法**:
```bash
./run-standalone-cdc.sh
```

### 中期方案: Flink CDC 3.x with Incremental Snapshot

如果需要历史数据，可以优化 Flink CDC 配置：

1. **增加并行度**: 提高 snapshot 速度
   ```java
   .splitSize(2048) // 减小 split 大小，增加并行度
   ```

2. **使用时间范围过滤**: 只 snapshot 最近的数据
   ```java
   // 在 Debezium 配置中添加
   debeziumProps.setProperty("snapshot.select.statement.overrides", 
       "FINANCE_USER.TRANS_INFO=SELECT * FROM TRANS_INFO WHERE TRANS_TIME > SYSDATE - 30");
   ```

3. **分阶段处理**:
   - 第一阶段: 使用 `StartupOptions.latest()` 捕获实时变更
   - 第二阶段: 单独运行批处理作业处理历史数据

### 长期方案: 企业级 CDC

对于生产环境，建议使用成熟的 CDC 解决方案：

1. **Oracle GoldenGate**:
   - Oracle 官方 CDC 解决方案
   - 企业级性能和可靠性
   - 支持复杂的数据转换

2. **AWS Database Migration Service (DMS)**:
   - 托管 CDC 服务
   - 支持多种数据库
   - 自动扩展和监控

3. **Debezium Server**:
   - 独立的 Debezium 服务
   - 不依赖 Kafka Connect
   - 支持多种输出格式

4. **Maxwell's Daemon** (for MySQL):
   - 轻量级 CDC 工具
   - 简单易用
   - 适合中小规模

## 当前部署状态

### Flink 集群
- ✅ JobManager: 运行中 (http://localhost:8081)
- ✅ TaskManager: 运行中 (1 个实例，4 个任务槽)

### Flink CDC 3.x 作业
- ✅ 作业状态: RUNNING
- ⚠️ 输出记录数: 0（卡在 snapshot 阶段）
- 📊 数据库表大小: 2,152,090,100 条记录（21亿+）
- ⏱️ Snapshot 预计耗时: 数小时到数天

### 输出文件
- 📁 `./output/cdc/`: CSV 文件目录
- 📄 最新文件: `cdc_events_20260213_100902.csv` (30KB)
- ⚠️ 当前作业尚未生成新文件

## 下一步行动

### 选项 A: 修改为 latest 模式（推荐） ⭐
```bash
# 1. 取消当前作业
curl -X PATCH http://localhost:8081/jobs/6868437613383535578929d55fc63b77?mode=cancel

# 2. 修改代码使用 StartupOptions.latest()
# 编辑 src/main/java/com/realtime/pipeline/FlinkCDC3App.java

# 3. 重新编译和提交
mvn clean package -DskipTests
./submit-flink-cdc-job.sh

# 4. 插入测试数据验证
docker exec oracle11g bash -c "source /home/oracle/.bash_profile && sqlplus system/helowin@helowin"
```

### 选项 B: 继续等待 snapshot 完成
```bash
# 监控作业进度
./check-flink-cdc3-status.sh

# 查看详细日志
docker logs -f realtime-pipeline-taskmanager-1
```

### 选项 C: 使用 Standalone CDC
```bash
# 停止 Flink CDC 作业
curl -X PATCH http://localhost:8081/jobs/6868437613383535578929d55fc63b77?mode=cancel

# 启动 Standalone CDC
./run-standalone-cdc.sh
```

## 相关文件

- `src/main/java/com/realtime/pipeline/FlinkOracleCDCApp.java` - Flink CDC 实现
- `src/main/java/com/realtime/pipeline/StandaloneCDCApp.java` - Standalone CDC 实现
- `run-flink-cdc.sh` - Flink CDC 运行脚本
- `run-standalone-cdc.sh` - Standalone CDC 运行脚本
- `submit-flink-cdc-job.sh` - Flink 作业提交脚本
- `CDC_IMPLEMENTATION_SUCCESS.md` - Standalone CDC 成功文档
- `QUICKSTART_CDC.md` - 快速开始指南

## 结论

Flink CDC 3.x 已成功升级并运行，但遇到了**大表 snapshot 性能问题**：

**关键发现**:
- ✅ Flink CDC 3.x 升级成功，无类加载错误
- ✅ 作业运行稳定，无异常
- ⚠️ TRANS_INFO 表有 21 亿+记录，snapshot 阶段耗时极长
- ⚠️ 当前作业卡在 snapshot 阶段，未进入 streaming 阶段

**推荐行动**:
1. **立即**: 修改为 `StartupOptions.latest()` 跳过 snapshot，只捕获新变更
2. **短期**: 如果需要快速验证，使用 Standalone CDC
3. **长期**: 考虑分阶段处理（实时 + 批处理）或使用企业级 CDC 方案

**技术验证**:
- Flink CDC 3.x 的 API 和依赖管理是正确的
- 对于大表场景，需要合理选择启动模式和优化配置
- 生产环境建议使用 `StartupOptions.latest()` 或增量 snapshot 策略
