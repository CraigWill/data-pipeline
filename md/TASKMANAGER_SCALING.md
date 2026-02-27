# TaskManager 容器扩展指南

**日期**: 2026-02-26  
**操作**: 扩展 TaskManager 容器实例

## 扩展结果

✅ **TaskManager 已成功扩展到 3 个实例**

### 容器状态

| 容器名称 | 状态 | 端口映射 |
|---------|------|---------|
| realtime-pipeline-taskmanager-1 | Up About an hour (healthy) | 6122→6121, 55657→6122, 55658→9249 |
| realtime-pipeline-taskmanager-2 | Up 26 seconds (healthy) | 6126→6121, 53655→6122, 53656→9249 |
| realtime-pipeline-taskmanager-3 | Up 26 seconds (healthy) | 6125→6121, 53643→6122, 53642→9249 |

### Flink 集群状态

| TaskManager | ID | 槽位数 | 可用槽位 | CPU 核心 | 内存 |
|------------|-----|--------|---------|---------|------|
| TM-1 | 172.19.0.3:6122-105722 | 4 | 0 | 2 | 2GB |
| TM-2 | 172.19.0.7:6122-194218 | 4 | 4 | 2 | 2GB |
| TM-3 | 172.19.0.8:6122-761f8b | 4 | 4 | 2 | 2GB |

**总计**:
- TaskManager 数量: 3 个
- 总槽位数: 12 个
- 可用槽位: 8 个
- 已使用槽位: 4 个（TM-1 正在运行作业）

## 扩展命令

### 基本扩展命令

```bash
# 扩展到 3 个 TaskManager
docker-compose up -d --scale taskmanager=3

# 扩展到 5 个 TaskManager
docker-compose up -d --scale taskmanager=5

# 缩减到 1 个 TaskManager
docker-compose up -d --scale taskmanager=1
```

### 验证扩展结果

```bash
# 查看所有 TaskManager 容器
docker ps --filter "name=taskmanager"

# 查看 docker-compose 服务状态
docker-compose ps taskmanager

# 查看 Flink Web UI 中的 TaskManager
curl -s http://localhost:8081/taskmanagers | python3 -c "
import sys, json
data = json.load(sys.stdin)
tms = data.get('taskmanagers', [])
print(f'TaskManager 总数: {len(tms)}')
for tm in tms:
    print(f\"  ID: {tm['id']}, 槽位: {tm['slotsNumber']}, 可用: {tm['freeSlots']}\")
"
```

## 扩展原理

### Docker Compose Scale

Docker Compose 的 `--scale` 参数允许动态调整服务的实例数量：

```yaml
# docker-compose.yml
taskmanager:
  # 注意：使用 scale 时不要设置 container_name
  # container_name: flink-taskmanager-1  # ❌ 错误
  image: realtime-pipeline-taskmanager
  ...
```

### 端口映射

当扩展多个实例时，Docker 会自动分配端口：

```yaml
ports:
  - "6121-6130:6121"  # Data Port (支持多个 TaskManager)
  - "6122"            # RPC Port (动态分配)
  - "9249"            # Prometheus Metrics (动态分配)
```

- **固定端口范围**: `6121-6130:6121` 表示宿主机的 6121-6130 端口映射到容器的 6121 端口
- **动态端口**: `6122` 和 `9249` 会由 Docker 自动分配宿主机端口

### 容器命名

扩展后的容器自动命名：
- `realtime-pipeline-taskmanager-1`
- `realtime-pipeline-taskmanager-2`
- `realtime-pipeline-taskmanager-3`
- ...

## 使用场景

### 1. 增加处理能力

当数据量增大，需要更多的处理能力时：

```bash
# 当前 1 个 TaskManager，4 个槽位
# 扩展到 3 个 TaskManager，12 个槽位
docker-compose up -d --scale taskmanager=3
```

**效果**:
- 槽位数: 4 → 12 (增加 3 倍)
- 并行度: 可以运行更多的并行任务
- 吞吐量: 理论上提升 3 倍

### 2. 高可用性

多个 TaskManager 提供容错能力：

```bash
# 扩展到 3 个 TaskManager
docker-compose up -d --scale taskmanager=3
```

**优势**:
- 一个 TaskManager 故障，其他继续工作
- Flink 会自动重新分配任务
- 提高系统可用性

### 3. 负载均衡

多个 TaskManager 分担负载：

```bash
# 根据负载动态调整
docker-compose up -d --scale taskmanager=5
```

**效果**:
- 任务分布在多个 TaskManager 上
- 避免单点过载
- 提高整体性能

### 4. 测试和开发

在开发环境测试不同规模：

```bash
# 测试小规模
docker-compose up -d --scale taskmanager=1

# 测试中等规模
docker-compose up -d --scale taskmanager=3

# 测试大规模
docker-compose up -d --scale taskmanager=10
```

## 扩展步骤

### 步骤 1: 检查当前状态

```bash
# 查看当前 TaskManager 数量
docker ps --filter "name=taskmanager" | wc -l

# 查看 Flink 集群状态
curl -s http://localhost:8081/overview
```

### 步骤 2: 执行扩展

```bash
# 扩展到目标数量（例如 3 个）
docker-compose up -d --scale taskmanager=3
```

### 步骤 3: 验证扩展

```bash
# 等待容器启动（约 30 秒）
sleep 30

# 检查容器状态
docker ps --filter "name=taskmanager"

# 检查 Flink 集群
curl -s http://localhost:8081/taskmanagers
```

### 步骤 4: 查看 Web UI

访问 Flink Web UI: http://localhost:8081

- 点击 "Task Managers" 查看所有 TaskManager
- 查看槽位分配情况
- 查看资源使用情况

## 注意事项

### 1. 资源限制

每个 TaskManager 都会消耗资源：

```yaml
# docker-compose.yml
deploy:
  resources:
    limits:
      cpus: '2.0'
      memory: 2G
```

**计算**:
- 1 个 TaskManager: 2 CPU, 2GB 内存
- 3 个 TaskManager: 6 CPU, 6GB 内存
- 5 个 TaskManager: 10 CPU, 10GB 内存

确保宿主机有足够的资源！

### 2. 端口冲突

固定端口范围限制了最大实例数：

```yaml
ports:
  - "6121-6130:6121"  # 最多支持 10 个实例
```

如果需要更多实例，扩大端口范围：

```yaml
ports:
  - "6121-6150:6121"  # 支持 30 个实例
```

### 3. 作业重启

扩展 TaskManager 不会自动重启作业，但会影响正在运行的作业：

- **增加 TaskManager**: 新的槽位可用，但现有作业不会自动使用
- **减少 TaskManager**: 可能导致作业失败（如果槽位不足）

**建议**: 扩展后重启作业以充分利用新资源：

```bash
# 扩展 TaskManager
docker-compose up -d --scale taskmanager=3

# 重启作业
./shell/restart-flink-cdc-job.sh
```

### 4. 数据一致性

扩展不会影响数据一致性：

- Checkpoint 机制保证数据不丢失
- 状态数据存储在共享存储中
- 新的 TaskManager 可以访问历史状态

### 5. 网络配置

所有 TaskManager 必须在同一个 Docker 网络中：

```yaml
networks:
  - flink-network
```

这样它们才能相互通信和连接到 JobManager。

## 缩减 TaskManager

### 缩减命令

```bash
# 缩减到 1 个 TaskManager
docker-compose up -d --scale taskmanager=1

# 缩减到 2 个 TaskManager
docker-compose up -d --scale taskmanager=2
```

### 缩减影响

- Docker 会停止多余的容器
- 如果作业正在使用被停止的 TaskManager，作业会失败
- 需要确保剩余的 TaskManager 有足够的槽位

### 安全缩减

1. **检查槽位使用情况**
   ```bash
   curl -s http://localhost:8081/taskmanagers | python3 -c "
   import sys, json
   data = json.load(sys.stdin)
   total_slots = sum(tm['slotsNumber'] for tm in data['taskmanagers'])
   used_slots = sum(tm['slotsNumber'] - tm['freeSlots'] for tm in data['taskmanagers'])
   print(f'总槽位: {total_slots}, 已使用: {used_slots}, 可用: {total_slots - used_slots}')
   "
   ```

2. **计算最小 TaskManager 数量**
   ```
   最小数量 = ceil(已使用槽位 / 每个TM的槽位数)
   ```

3. **执行缩减**
   ```bash
   docker-compose up -d --scale taskmanager=<最小数量>
   ```

## 监控和管理

### 监控命令

```bash
# 实时监控 TaskManager 状态
watch -n 5 'docker ps --filter "name=taskmanager" --format "table {{.Names}}\t{{.Status}}"'

# 监控 Flink 集群
watch -n 5 'curl -s http://localhost:8081/overview | python3 -c "import sys, json; data = json.load(sys.stdin); print(f\"TaskManagers: {data[\"taskmanagers\"]}, Slots: {data[\"slots-total\"]}, Available: {data[\"slots-available\"]}\")"'
```

### 查看日志

```bash
# 查看特定 TaskManager 的日志
docker logs realtime-pipeline-taskmanager-1 --tail 100

# 查看所有 TaskManager 的日志
for i in {1..3}; do
    echo "=== TaskManager $i ==="
    docker logs realtime-pipeline-taskmanager-$i --tail 20
    echo ""
done
```

### 资源使用

```bash
# 查看容器资源使用
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | grep taskmanager
```

## 自动扩展

### 基于负载的自动扩展脚本

```bash
#!/bin/bash
# auto-scale-taskmanager.sh

# 配置
MIN_TM=1
MAX_TM=10
TARGET_SLOT_USAGE=0.8  # 80% 槽位使用率

while true; do
    # 获取槽位使用情况
    STATS=$(curl -s http://localhost:8081/overview)
    TOTAL_SLOTS=$(echo $STATS | python3 -c "import sys, json; print(json.load(sys.stdin)['slots-total'])")
    AVAILABLE_SLOTS=$(echo $STATS | python3 -c "import sys, json; print(json.load(sys.stdin)['slots-available'])")
    USED_SLOTS=$((TOTAL_SLOTS - AVAILABLE_SLOTS))
    
    # 计算使用率
    USAGE=$(echo "scale=2; $USED_SLOTS / $TOTAL_SLOTS" | bc)
    
    echo "$(date): 槽位使用率: $USAGE ($USED_SLOTS/$TOTAL_SLOTS)"
    
    # 判断是否需要扩展
    if (( $(echo "$USAGE > $TARGET_SLOT_USAGE" | bc -l) )); then
        CURRENT_TM=$(docker ps --filter "name=taskmanager" | wc -l)
        CURRENT_TM=$((CURRENT_TM - 1))  # 减去标题行
        
        if [ $CURRENT_TM -lt $MAX_TM ]; then
            NEW_TM=$((CURRENT_TM + 1))
            echo "扩展 TaskManager: $CURRENT_TM -> $NEW_TM"
            docker-compose up -d --scale taskmanager=$NEW_TM
        fi
    fi
    
    sleep 60  # 每分钟检查一次
done
```

## 最佳实践

### 1. 初始规模

根据数据量和并行度确定初始 TaskManager 数量：

```
TaskManager 数量 = ceil(并行度 / 每个TM的槽位数)
```

例如：
- 并行度 = 8
- 每个 TM 槽位 = 4
- TaskManager 数量 = ceil(8/4) = 2

### 2. 预留容量

保留一些空闲槽位用于故障恢复：

```
建议槽位使用率 < 80%
```

### 3. 逐步扩展

不要一次扩展太多，逐步增加：

```bash
# 从 1 扩展到 2
docker-compose up -d --scale taskmanager=2
sleep 60

# 从 2 扩展到 3
docker-compose up -d --scale taskmanager=3
sleep 60

# 从 3 扩展到 5
docker-compose up -d --scale taskmanager=5
```

### 4. 监控资源

持续监控资源使用情况：

```bash
# CPU 和内存使用
docker stats --no-stream | grep taskmanager

# 网络流量
docker stats --no-stream --format "table {{.Name}}\t{{.NetIO}}" | grep taskmanager
```

### 5. 定期清理

定期清理不需要的容器：

```bash
# 停止所有 TaskManager
docker-compose stop taskmanager

# 删除停止的容器
docker-compose rm -f taskmanager

# 重新启动指定数量
docker-compose up -d --scale taskmanager=3
```

## 故障排查

### 问题 1: TaskManager 无法注册

**症状**: 容器启动但未在 Flink Web UI 中显示

**解决方案**:
```bash
# 检查 JobManager 连接
docker logs realtime-pipeline-taskmanager-1 | grep "JobManager"

# 检查网络连接
docker exec realtime-pipeline-taskmanager-1 ping -c 3 jobmanager

# 重启 TaskManager
docker-compose restart taskmanager
```

### 问题 2: 端口冲突

**症状**: 容器启动失败，提示端口已被占用

**解决方案**:
```bash
# 检查端口占用
lsof -i :6121-6130

# 扩大端口范围（修改 docker-compose.yml）
ports:
  - "6121-6150:6121"

# 重新启动
docker-compose up -d --scale taskmanager=3
```

### 问题 3: 资源不足

**症状**: 容器启动缓慢或失败

**解决方案**:
```bash
# 检查系统资源
docker system df
free -h
top

# 减少 TaskManager 数量
docker-compose up -d --scale taskmanager=2

# 或减少每个 TM 的资源限制
```

## 相关文档

- `docker-compose.yml` - Docker Compose 配置
- `md/CURRENT_CDC_STATUS.md` - 当前 CDC 状态
- `md/OUTPUT_DIRECTORY_MAPPING.md` - Output 目录映射
- `README.md` - 项目主文档

## 总结

✅ **TaskManager 扩展成功**

- 从 1 个扩展到 3 个 TaskManager
- 总槽位从 4 个增加到 12 个
- 可用槽位: 8 个
- 所有容器健康运行

**扩展命令**:
```bash
docker-compose up -d --scale taskmanager=3
```

**验证命令**:
```bash
docker ps --filter "name=taskmanager"
curl -s http://localhost:8081/taskmanagers
```

**Web UI**: http://localhost:8081/#/task-managers

现在你的 Flink 集群有更强的处理能力了！
