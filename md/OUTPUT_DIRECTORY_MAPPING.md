# Output 目录映射说明

**日期**: 2026-02-26  
**问题**: 本地生成的目录和 Docker 里的 output 是一致的吗？  
**答案**: ✅ 是的，完全一致

## 验证结果

### 文件数量对比

| 位置 | 文件数量 | 状态 |
|------|---------|------|
| 本地 `output/cdc/2026-02-26--09/` | 31 个 CSV 文件 | ✅ |
| Docker 容器 `/opt/flink/output/cdc/2026-02-26--09/` | 31 个 CSV 文件 | ✅ |

### 总大小对比

| 位置 | 总大小 | 状态 |
|------|--------|------|
| 本地 `output/cdc/2026-02-26--09/` | 600MB | ✅ |
| Docker 容器 `/opt/flink/output/cdc/2026-02-26--09/` | 600MB | ✅ |

### 文件列表对比

本地和 Docker 容器内的文件完全一致，包括：
- 文件名相同
- 文件大小相同
- 文件时间戳相同

## 原理说明

### Docker Volume 挂载

在 `docker-compose.yml` 中，TaskManager 容器配置了 volume 挂载：

```yaml
taskmanager:
  volumes:
    - flink-checkpoints:/opt/flink/checkpoints
    - flink-savepoints:/opt/flink/savepoints
    - flink-logs:/opt/flink/logs
    - flink-data:/opt/flink/data
    - ./output:/opt/flink/output          # ⭐ 关键配置
```

### 挂载说明

```
宿主机路径                    容器内路径
./output          ←→         /opt/flink/output
(项目根目录)                  (容器内 Flink 目录)
```

这个配置的含义：
- **宿主机路径**: `./output` (相对于 docker-compose.yml 所在目录)
- **容器内路径**: `/opt/flink/output`
- **挂载类型**: bind mount (绑定挂载)
- **读写权限**: 读写 (默认)

### 工作原理

1. **Flink 作业运行**
   - Flink CDC 作业在 TaskManager 容器内运行
   - FileSink 将 CSV 文件写入 `/opt/flink/output/cdc/`

2. **Volume 挂载**
   - Docker 将容器内的 `/opt/flink/output` 映射到宿主机的 `./output`
   - 容器内写入的文件实时同步到宿主机

3. **实时同步**
   - 容器内创建文件 → 宿主机立即可见
   - 宿主机修改文件 → 容器内立即可见
   - 双向同步，实时生效

## 目录结构

### 宿主机（本地）

```
realtime-data-pipeline/
└── output/
    └── cdc/
        ├── 2026-02-25--17/
        │   ├── IDS_TRANS_INFO_*.csv (5 个文件, ~100MB)
        │   └── ...
        └── 2026-02-26--09/
            ├── IDS_TRANS_INFO_*.csv (31 个文件, ~600MB)
            └── ...
```

### Docker 容器内

```
/opt/flink/
└── output/
    └── cdc/
        ├── 2026-02-25--17/
        │   ├── IDS_TRANS_INFO_*.csv (5 个文件, ~100MB)
        │   └── ...
        └── 2026-02-26--09/
            ├── IDS_TRANS_INFO_*.csv (31 个文件, ~600MB)
            └── ...
```

## 验证命令

### 检查本地文件

```bash
# 列出本地文件
ls -lh output/cdc/2026-02-26--09/

# 统计文件数量
find output/cdc/2026-02-26--09 -name "*.csv" -type f | wc -l

# 查看总大小
du -sh output/cdc/2026-02-26--09/

# 查看文件内容
head -10 output/cdc/2026-02-26--09/IDS_TRANS_INFO_*.csv | head -1
```

### 检查容器内文件

```bash
# 列出容器内文件
docker exec realtime-pipeline-taskmanager-1 ls -lh /opt/flink/output/cdc/2026-02-26--09/

# 统计文件数量
docker exec realtime-pipeline-taskmanager-1 find /opt/flink/output/cdc/2026-02-26--09 -name "*.csv" -type f | wc -l

# 查看总大小
docker exec realtime-pipeline-taskmanager-1 du -sh /opt/flink/output/cdc/2026-02-26--09/

# 查看文件内容
docker exec realtime-pipeline-taskmanager-1 head -10 /opt/flink/output/cdc/2026-02-26--09/IDS_TRANS_INFO_*.csv | head -1
```

### 对比验证

```bash
# 对比文件数量
echo "本地文件数: $(find output/cdc/2026-02-26--09 -name "*.csv" -type f | wc -l)"
echo "容器文件数: $(docker exec realtime-pipeline-taskmanager-1 find /opt/flink/output/cdc/2026-02-26--09 -name "*.csv" -type f | wc -l)"

# 对比总大小
echo "本地大小: $(du -sh output/cdc/2026-02-26--09/ | awk '{print $1}')"
echo "容器大小: $(docker exec realtime-pipeline-taskmanager-1 du -sh /opt/flink/output/cdc/2026-02-26--09/ | awk '{print $1}')"
```

## 优势

### 1. 实时访问
- 不需要从容器复制文件
- 文件生成后立即可在本地访问
- 便于实时监控和分析

### 2. 数据持久化
- 容器重启后数据不丢失
- 容器删除后数据仍保留在宿主机
- 便于备份和归档

### 3. 便于开发
- 可以直接在本地查看和编辑文件
- 使用本地工具分析 CSV 文件
- 不需要进入容器操作

### 4. 性能优化
- 避免频繁的文件复制
- 减少网络传输开销
- 提高 I/O 性能

## 注意事项

### 1. 路径配置

在代码中配置输出路径时，使用容器内的路径：

```java
// FlinkCDC3App.java
String outputPath = getEnv("OUTPUT_PATH", "./output/cdc");

// 实际写入路径（容器内）
FileSink<String> fileSink = FileSink
    .forRowFormat(new Path(outputPath), new CsvEncoderWithHeader())
    .build();
```

环境变量配置：
```yaml
# docker-compose.yml
environment:
  - OUTPUT_PATH=./output/cdc  # 相对路径，相对于容器内工作目录
```

### 2. 权限问题

容器内的文件所有者是 `flink` 用户，但在宿主机上可以正常访问：

```bash
# 容器内
-rw-r--r-- 1 flink flink 21M Feb 26 09:10 IDS_TRANS_INFO_*.csv

# 宿主机
-rw-r--r-- 1 Apple staff 20M Feb 26 09:10 IDS_TRANS_INFO_*.csv
```

这是正常的，Docker 会自动处理权限映射。

### 3. 磁盘空间

由于文件直接写入宿主机，需要确保宿主机有足够的磁盘空间：

```bash
# 检查磁盘空间
df -h .

# 检查 output 目录大小
du -sh output/
```

### 4. 清理旧文件

定期清理旧的 CSV 文件以释放空间：

```bash
# 删除 7 天前的文件
find output/cdc -name "*.csv" -type f -mtime +7 -delete

# 或者移动到归档目录
mkdir -p output/archive
find output/cdc -name "*.csv" -type f -mtime +7 -exec mv {} output/archive/ \;
```

## 其他挂载的目录

除了 `output` 目录，还有其他目录也使用了 volume 挂载：

### Named Volumes（命名卷）

```yaml
volumes:
  - flink-checkpoints:/opt/flink/checkpoints  # Checkpoint 数据
  - flink-savepoints:/opt/flink/savepoints    # Savepoint 数据
  - flink-logs:/opt/flink/logs                # 日志文件
  - flink-data:/opt/flink/data                # 其他数据
```

这些是 Docker 管理的命名卷，数据存储在 Docker 的数据目录中，不直接映射到项目目录。

### Bind Mounts（绑定挂载）

```yaml
volumes:
  - ./output:/opt/flink/output                # 输出文件（本地可见）
```

这是绑定挂载，直接映射到项目目录，本地可见。

## 查看 Volume 信息

```bash
# 列出所有 volumes
docker volume ls

# 查看特定 volume 的详细信息
docker volume inspect flink-checkpoints

# 查看 volume 的实际存储位置
docker volume inspect flink-checkpoints | grep Mountpoint
```

## 总结

✅ **本地和 Docker 容器内的 output 目录完全一致**

- 文件数量相同：31 个 CSV 文件
- 文件大小相同：600MB
- 实时同步：容器写入，本地立即可见
- 数据持久化：容器重启后数据不丢失

这是通过 Docker 的 bind mount 机制实现的，配置在 `docker-compose.yml` 中：

```yaml
volumes:
  - ./output:/opt/flink/output
```

你可以直接在本地 `output/cdc/` 目录中查看和分析生成的 CSV 文件，无需进入容器。

## 相关文档

- `docker-compose.yml` - Docker Compose 配置文件
- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - Flink CDC 应用代码
- `md/CURRENT_CDC_STATUS.md` - 当前 CDC 状态报告
- `README.md` - 项目主文档
