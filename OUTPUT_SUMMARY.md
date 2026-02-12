# 输出文件总结

## 输出位置

### 容器内
```
/opt/flink/output/cdc/
├── 2026-02-12--01/    # 第一批数据
├── 2026-02-12--03/    # 最新数据
└── dlq/               # 死信队列（错误数据）
```

### 本地（已复制）
```
output/cdc/
├── 2026-02-12--01/
├── 2026-02-12--03/
└── dlq/
```

## 文件格式

### CSV 格式
```csv
时间戳,表名,操作类型,数据字段...
2026-02-12 03:14:57,trans_info,DELETE,"name=sample_name_0","id=0","value=46","timestamp=1770866097020",
2026-02-12 03:15:02,trans_info,UPDATE,"name=sample_name_1","id=1","value=811","timestamp=1770866102080",
2026-02-12 03:15:07,trans_info,INSERT,"name=sample_name_2","id=2","value=586","timestamp=1770866107088",
```

### 字段说明
1. **时间戳**: 事件处理时间
2. **表名**: trans_info
3. **操作类型**: INSERT, UPDATE, DELETE
4. **数据字段**: 键值对格式，包含 id, name, value, timestamp

## 查看输出文件

### 方法 1: 使用查看脚本（推荐）
```bash
./view-output.sh
```

### 方法 2: 直接查看本地文件
```bash
# 查看最新文件
find output/cdc -type f -name "*.inprogress.*" | xargs ls -t | head -1 | xargs cat

# 查看所有文件
cat output/cdc/2026-*/.part-*.inprogress.*

# 统计记录数
cat output/cdc/2026-*/.part-*.inprogress.* | wc -l
```

### 方法 3: 从容器实时查看
```bash
# 实时监控新数据
docker exec realtime-pipeline-taskmanager-1 \
  sh -c 'tail -f /opt/flink/output/cdc/2026-*/.*inprogress.*'

# 查看容器内文件列表
docker exec realtime-pipeline-taskmanager-1 \
  find /opt/flink/output/cdc -type f
```

## 文件更新

### 自动更新
文件会持续更新，因为 Flink 作业正在运行并生成模拟数据。

### 手动同步
```bash
# 从容器复制最新文件到本地
docker cp realtime-pipeline-taskmanager-1:/opt/flink/output/cdc/. output/cdc/
```

## 文件滚动策略

根据配置，文件会在以下情况下滚动（创建新文件）：
- 每 5 分钟
- 文件大小达到 128MB
- 不活动 2 分钟后

## 当前状态

✅ **正在生成数据**
- 作业状态: RUNNING
- 数据源: SimpleCDCApp 模拟数据
- 输出格式: CSV
- 更新频率: 每 5 秒一条记录

## 数据统计

### 最新文件
- 文件: `2026-02-12--03/.part-*.inprogress.*`
- 大小: ~6.2KB
- 记录数: ~60 条
- 更新时间: 实时更新中

### 历史文件
- 目录: `2026-02-12--01/`
- 包含之前运行的数据

## 验证数据

### 检查数据完整性
```bash
# 查看前 10 条记录
cat output/cdc/2026-*/.part-*.inprogress.* | head -10

# 查看最后 10 条记录
cat output/cdc/2026-*/.part-*.inprogress.* | tail -10

# 统计各操作类型数量
cat output/cdc/2026-*/.part-*.inprogress.* | cut -d',' -f3 | sort | uniq -c
```

### 示例输出
```
  20 DELETE
  21 INSERT
  19 UPDATE
```

## 故障排查

### 如果看不到文件

1. **检查作业状态**
```bash
docker exec flink-jobmanager flink list
```

2. **检查容器内文件**
```bash
docker exec realtime-pipeline-taskmanager-1 \
  ls -la /opt/flink/output/cdc/
```

3. **查看日志**
```bash
docker logs realtime-pipeline-taskmanager-1 | tail -50
```

### 如果文件不更新

1. **检查作业是否运行**
```bash
docker exec flink-jobmanager flink list
```

2. **重启作业**
```bash
# 取消当前作业
docker exec flink-jobmanager flink cancel <job-id>

# 重新提交
./submit-to-flink.sh simple
```

## 下一步

### 分析数据
```bash
# 使用 Python 分析
python3 << EOF
import pandas as pd
import glob

files = glob.glob('output/cdc/2026-*/.part-*.inprogress.*')
for f in files:
    df = pd.read_csv(f, header=None, 
                     names=['timestamp', 'table', 'operation', 'data'])
    print(f"File: {f}")
    print(f"Records: {len(df)}")
    print(df['operation'].value_counts())
    print()
EOF
```

### 导出数据
```bash
# 合并所有文件
cat output/cdc/2026-*/.part-*.inprogress.* > output/all_cdc_data.csv

# 压缩归档
tar -czf output/cdc_backup_$(date +%Y%m%d_%H%M%S).tar.gz output/cdc/
```

## 总结

✅ 输出文件已成功生成并可以查看
✅ 数据格式正确（CSV）
✅ 包含完整的 CDC 事件信息
✅ 文件持续更新中

使用 `./view-output.sh` 可以随时查看最新的输出文件。
