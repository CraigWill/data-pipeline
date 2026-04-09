# CDC 分片大小（Split Size）说明

## 什么是分片大小？

**分片大小（Split Size）不是指文件大小**，而是指 Flink CDC 在读取数据库表时的**数据分片策略参数**。

## 详细说明

### 1. 用途

Split Size 用于控制 Flink CDC 在**全量读取阶段**（Snapshot Phase）如何将大表分割成多个小的数据块（Split）进行并行读取。

### 2. 工作原理

在 Flink CDC 的全量读取阶段：

```
大表（1000万行）
    ↓ 按 Split Size 分割
    ├─ Split 1: 行 1 - 8096
    ├─ Split 2: 行 8097 - 16192
    ├─ Split 3: 行 16193 - 24288
    └─ ...
```

每个 Split 可以被不同的 Task 并行处理，提高全量读取的效率。

### 3. 参数含义

- **单位**：行数（Rows）
- **默认值**：8096 行
- **作用范围**：仅在全量读取阶段生效
- **增量阶段**：不影响增量读取（LogMiner）

### 4. 配置示例

```java
JdbcIncrementalSource<String> oracleSource = new OracleSourceBuilder<String>()
    .hostname(hostname)
    .port(port)
    .databaseList(database)
    .schemaList(schema)
    .tableList(tableList)
    .username(username)
    .password(password)
    .splitSize(8096)  // 每个分片包含 8096 行数据
    .build();
```

## 如何选择合适的值？

### 小表（< 10万行）

```
推荐值: 8096 - 16384
原因: 小表不需要太多分片，避免过度分割
```

### 中等表（10万 - 100万行）

```
推荐值: 16384 - 32768
原因: 平衡分片数量和单个分片大小
```

### 大表（> 100万行）

```
推荐值: 32768 - 65536
原因: 减少分片数量，避免过多的元数据开销
```

### 超大表（> 1000万行）

```
推荐值: 65536 - 131072
原因: 大分片减少调度开销，但需要更多内存
```

## 影响因素

### 1. 并行度（Parallelism）

```
Split Size × Parallelism = 同时处理的行数

例如:
- Split Size: 8096
- Parallelism: 4
- 同时处理: 32384 行
```

### 2. 内存使用

```
Split Size 越大 → 单个 Task 内存占用越高
Split Size 越小 → 分片数量越多，元数据开销越大
```

### 3. 读取速度

```
Split Size 太小:
  ✗ 分片过多，调度开销大
  ✗ 频繁的数据库查询
  ✗ 元数据管理复杂

Split Size 太大:
  ✗ 单个 Task 处理时间长
  ✗ 内存占用高
  ✗ 并行度利用不充分

Split Size 适中:
  ✓ 平衡并行度和开销
  ✓ 充分利用资源
  ✓ 稳定的读取性能
```

## 实际案例

### 案例 1：小表快速读取

```yaml
表大小: 5万行
Split Size: 8096
Parallelism: 2

结果:
- 分片数: 7 个 (50000 / 8096 ≈ 6.2)
- 每个 Task 处理: 3-4 个分片
- 读取时间: 约 30 秒
```

### 案例 2：大表并行读取

```yaml
表大小: 500万行
Split Size: 32768
Parallelism: 4

结果:
- 分片数: 153 个 (5000000 / 32768 ≈ 152.6)
- 每个 Task 处理: 38-39 个分片
- 读取时间: 约 10 分钟
```

### 案例 3：超大表优化

```yaml
表大小: 1亿行
Split Size: 131072
Parallelism: 8

结果:
- 分片数: 763 个 (100000000 / 131072 ≈ 762.9)
- 每个 Task 处理: 95-96 个分片
- 读取时间: 约 2 小时
```

## 与文件大小的关系

虽然 Split Size 不是文件大小，但它会**间接影响**输出文件的大小：

```
Split Size 影响读取速度
    ↓
读取速度影响数据流速率
    ↓
数据流速率影响文件滚动频率
    ↓
文件滚动频率影响单个文件大小
```

### 示例

```
Split Size: 8096
读取速度: 1000 行/秒
文件滚动间隔: 60 秒

预期文件大小:
- 行数: 60000 行
- 大小: 约 5-10 MB（取决于行宽度）
```

## 配置建议

### 开发环境

```yaml
Split Size: 8096
Parallelism: 2
原因: 快速测试，资源占用少
```

### 测试环境

```yaml
Split Size: 16384
Parallelism: 4
原因: 模拟生产负载
```

### 生产环境

```yaml
Split Size: 32768 - 65536
Parallelism: 4 - 8
原因: 平衡性能和资源使用
```

## 监控指标

### 关键指标

1. **全量读取时间**
   - 目标：< 表大小 / 10000 秒
   - 例如：100万行表 < 100 秒

2. **内存使用**
   - 目标：< TaskManager 内存的 70%
   - 监控：Flink Web UI → Task Managers

3. **分片处理速度**
   - 目标：均匀分布，无明显慢分片
   - 监控：Flink Web UI → Jobs → Subtasks

4. **数据库负载**
   - 目标：CPU < 80%, IO < 70%
   - 监控：数据库监控工具

## 调优步骤

### 1. 基线测试

```bash
# 使用默认值测试
Split Size: 8096
Parallelism: 2

# 记录:
- 全量读取时间
- 内存使用峰值
- 数据库负载
```

### 2. 增加并行度

```bash
# 保持 Split Size，增加并行度
Split Size: 8096
Parallelism: 4

# 观察:
- 读取时间是否减半
- 内存使用是否翻倍
```

### 3. 调整分片大小

```bash
# 如果内存充足，增加 Split Size
Split Size: 16384
Parallelism: 4

# 观察:
- 分片数量减少
- 单个分片处理时间增加
- 总体时间是否优化
```

### 4. 找到最优值

```bash
# 多次测试，找到最佳组合
测试组合:
1. Split Size: 8096,  Parallelism: 2
2. Split Size: 16384, Parallelism: 4
3. Split Size: 32768, Parallelism: 4
4. Split Size: 32768, Parallelism: 8

选择:
- 读取时间最短
- 资源使用合理
- 稳定性最好
```

## 常见问题

### Q1: Split Size 设置为 0 会怎样？

A: 使用默认值 8096。代码中有保护逻辑：

```java
int splitSize = request.getSplitSize() > 0 ? request.getSplitSize() : 8096;
```

### Q2: Split Size 可以设置为负数吗？

A: 不可以。会被当作 0 处理，最终使用默认值 8096。

### Q3: Split Size 有最大值限制吗？

A: 理论上没有硬性限制，但建议不超过 1048576（1M 行），否则：
- 单个 Task 内存占用过高
- 处理时间过长
- 失败重试代价大

### Q4: 增量阶段会使用 Split Size 吗？

A: 不会。Split Size 仅用于全量读取阶段。增量阶段使用 LogMiner 连续读取 Redo Log，不涉及分片。

### Q5: 如何知道当前使用的 Split Size？

A: 查看 Flink Job 日志：

```bash
docker logs flink-jobmanager 2>&1 | grep splitSize
```

或查看任务配置详情。

## 总结

| 方面 | 说明 |
|------|------|
| **定义** | 全量读取时每个数据分片包含的行数 |
| **单位** | 行数（Rows） |
| **默认值** | 8096 |
| **作用阶段** | 仅全量读取（Snapshot） |
| **影响** | 读取速度、内存使用、并行度利用 |
| **不是** | 文件大小、内存大小、缓冲区大小 |

**关键点**：Split Size 是数据库读取优化参数，不是文件系统参数。它控制如何高效地从数据库读取大表，而不是控制输出文件的大小。
