# CDC 实现状态总结

## 当前状态

### ✅ 轮询方式 CDC（JdbcCDCApp）- 正常运行
- **状态**: 运行中
- **作业 ID**: cbd633d38dbf961000fd164e36e82289
- **输出**: `output/cdc/`
- **延迟**: 10 秒
- **捕获操作**: INSERT
- **输出格式**: CSV

### ⚠️ LogMiner CDC（OracleCDCApp）- 遇到技术问题
- **状态**: 无法部署
- **问题**: Flink CDC 连接器依赖冲突
- **错误**: `ServiceConfigurationError: org.infinispan.commons.jdkspecific.ClasspathURLStreamHandlerProvider not found`

## 问题分析

Flink CDC Oracle 连接器（版本 2.4.2）与当前的 Flink 环境存在类加载冲突，特别是 Infinispan 库的问题。这是一个已知的兼容性问题。

## 解决方案

### 方案 1: 继续使用轮询方式（推荐）

**优点：**
- ✅ 已经在正常工作
- ✅ 无需额外配置
- ✅ 稳定可靠

**缺点：**
- ❌ 只能捕获 INSERT 操作
- ❌ 延迟较高（10秒）

**当前输出示例：**
```csv
2026-02-12 03:30:06,trans_info,INSERT,23,"sample_data_23"
2026-02-12 03:30:16,trans_info,INSERT,24,"sample_data_24"
```

**查看输出：**
```bash
ls -la output/cdc/
tail -20 output/cdc/2026-02-12--*/part-*
```

---

### 方案 2: 改进轮询方式以支持 UPDATE/DELETE

我们可以修改 JdbcCDCApp 来支持更多操作类型：

1. **添加时间戳列跟踪**
   - 在表中添加 `updated_at` 列
   - 根据时间戳捕获变更

2. **使用触发器**
   - 创建 Oracle 触发器记录变更到审计表
   - 轮询审计表获取所有操作类型

3. **使用 Flashback Query**
   - 使用 Oracle Flashback 技术查询历史变更
   - 无需修改表结构

---

### 方案 3: 解决 Flink CDC 依赖冲突（高级）

需要以下步骤：

1. **降级或升级 Flink CDC 版本**
   ```xml
   <dependency>
       <groupId>com.ververica</groupId>
       <artifactId>flink-connector-oracle-cdc</artifactId>
       <version>3.0.0</version>  <!-- 尝试更新版本 -->
   </dependency>
   ```

2. **排除冲突的依赖**
   ```xml
   <exclusions>
       <exclusion>
           <groupId>org.infinispan</groupId>
           <artifactId>*</artifactId>
       </exclusion>
   </exclusions>
   ```

3. **使用自定义类加载器**
   - 修改 Flink 配置
   - 隔离 CDC 连接器的类加载

---

### 方案 4: 使用 Debezium 独立部署

不使用 Flink CDC 连接器，而是：

1. **部署 Debezium Server**
   - 独立运行 Debezium
   - 输出到 Kafka

2. **Flink 消费 Kafka**
   - Flink 从 Kafka 读取 CDC 事件
   - 处理并输出

**架构：**
```
Oracle DB → Debezium Server → Kafka → Flink → Output
```

## 推荐方案

### 短期（立即可用）
**继续使用轮询方式（JdbcCDCApp）**

当前作业正在正常运行，可以满足基本的 CDC 需求。

### 中期（1-2 周）
**改进轮询方式以支持 UPDATE/DELETE**

选择以下一种方式：
1. 添加时间戳列（最简单）
2. 使用触发器（最完整）
3. 使用 Flashback Query（无需修改表）

### 长期（1-2 月）
**部署 Debezium + Kafka 架构**

这是生产环境的最佳实践：
- 完整的 CDC 功能
- 高性能和低延迟
- 易于扩展和维护

## 当前可用命令

### 查看作业状态
```bash
# 通过 Web UI
open http://localhost:8081

# 查看日志
docker logs -f realtime-pipeline-taskmanager-1
```

### 查看输出
```bash
# 列出输出文件
ls -la output/cdc/

# 查看最新数据
tail -50 output/cdc/2026-02-12--*/part-*

# 统计行数
wc -l output/cdc/2026-02-12--*/part-*
```

### 监控数据库
```bash
# 检查数据库连接
nc -z localhost 1521

# 查看环境变量
cat .env | grep DATABASE
```

## 下一步行动

### 选项 A: 保持现状
继续使用当前的轮询方式，满足基本需求。

### 选项 B: 改进轮询方式
选择一种改进方案（时间戳/触发器/Flashback），我可以帮你实现。

### 选项 C: 研究 Debezium 方案
规划 Debezium + Kafka 架构的部署。

## 参考文档

- [CDC 方式对比](CDC_COMPARISON.md)
- [快速入门指南](QUICK_START_CDC.md)
- [Oracle LogMiner 配置](docs/ORACLE_CDC_LOGMINER.md)
- [本地 DataHub 部署](docs/LOCAL_DATAHUB_DEPLOYMENT.md)

## 技术支持

如果需要实现任何改进方案，请告诉我你的选择，我会提供详细的实现步骤。
