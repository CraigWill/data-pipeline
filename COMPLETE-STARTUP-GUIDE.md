# 完整启动指南

## 当前状态

✅ 项目已重构为多模块结构
✅ JobManager 镜像已构建并成功启动
✅ start.sh 脚本已更新
⏳ TaskManager 镜像待构建

## 快速启动步骤

### 1. 确认 JAR 文件已构建

```bash
# 检查 JAR 文件
ls -lh flink-jobs/target/flink-jobs-*.jar
ls -lh monitor-backend/target/monitor-backend-*.jar
```

如果文件不存在，运行：
```bash
./quick-build.sh
```

### 2. 构建 TaskManager 镜像

```bash
# 使用国内镜像源（推荐）
docker build -f docker/taskmanager/Dockerfile.cn -t flink-taskmanager:latest .

# 如果遇到网络问题，可以多试几次
```

### 3. 启动所有服务

```bash
# 启动所有服务
docker-compose up -d

# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 4. 验证服务

访问以下地址：
- **Flink Web UI**: http://localhost:8081
- **Monitor Backend**: http://localhost:5001
- **Monitor Frontend**: http://localhost:8888

## 详细步骤

### 步骤 1: 构建 Java 项目

```bash
# 方法 1: 使用快速构建脚本（推荐）
./quick-build.sh

# 方法 2: 手动构建
JAVA_HOME=$(/usr/libexec/java_home -v 11) mvn clean install -DskipTests
```

预期输出：
```
✓ Flink Jobs JAR: flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar 129M
✓ Monitor Backend JAR: monitor-backend/target/monitor-backend-1.0.0-SNAPSHOT.jar 89M
```

### 步骤 2: 构建 Docker 镜像

#### 2.1 构建 JobManager 镜像（已完成）

```bash
docker build -f docker/jobmanager/Dockerfile.cn -t flink-jobmanager:latest .
```

#### 2.2 构建 TaskManager 镜像

```bash
docker build -f docker/taskmanager/Dockerfile.cn -t flink-taskmanager:latest .
```

如果遇到网络问题：
```bash
# 重试几次
for i in {1..3}; do
    docker build -f docker/taskmanager/Dockerfile.cn -t flink-taskmanager:latest . && break
    echo "重试 $i/3..."
    sleep 5
done
```

#### 2.3 验证镜像

```bash
docker images | grep flink
```

应该看到：
```
flink-jobmanager    latest    xxx    xxx ago    2.69GB
flink-taskmanager   latest    xxx    xxx ago    xxx
```

### 步骤 3: 启动服务

#### 3.1 启动所有服务

```bash
docker-compose up -d
```

#### 3.2 查看服务状态

```bash
# 使用 start.sh 脚本
./start.sh --status

# 或使用 docker-compose
docker-compose ps
```

预期输出：
```
NAME                       STATUS
zookeeper                  Up (healthy)
flink-jobmanager           Up
flink-jobmanager-standby   Up
flink-taskmanager-1        Up
flink-taskmanager-2        Up
flink-taskmanager-3        Up
flink-monitor-backend      Up
flink-monitor-frontend     Up
```

#### 3.3 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 只查看 Flink 相关日志
docker-compose logs -f jobmanager taskmanager

# 查看特定服务日志
docker logs flink-jobmanager -f
```

### 步骤 4: 验证 CDC 任务

#### 4.1 访问 Flink Web UI

打开浏览器访问: http://localhost:8081

应该看到：
- JobManager 运行中
- 3 个 TaskManager 已连接
- CDC 任务 `cdc-no-duplicate` 正在运行（从 checkpoint 恢复）

#### 4.2 检查任务状态

在 Flink Web UI 中：
1. 点击 "Running Jobs"
2. 查看任务详情
3. 确认所有算子都在运行

#### 4.3 测试 CDC 功能

```sql
-- 在 Oracle 数据库中插入测试数据
INSERT INTO IDS_ACCOUNT_INFO (ACCOUNT_ID, ACCOUNT_NAME) 
VALUES ('TEST001', 'Test Account');
COMMIT;
```

检查输出文件：
```bash
ls -lh output/cdc/$(date +%Y-%m-%d--*)/
```

应该看到新生成的 CSV 文件。

## 常见问题

### Q1: TaskManager 镜像构建失败

**错误**: `apt-get update` 失败，exit code 100

**解决方案**:
1. 检查网络连接
2. 重试构建（网络问题通常是临时的）
3. 使用标准版 Dockerfile（如果国内镜像源不可用）

```bash
# 使用标准版
docker build -f docker/taskmanager/Dockerfile -t flink-taskmanager:latest .
```

### Q2: JAR 文件找不到

**错误**: `ls: target/realtime-data-pipeline-*.jar: No such file or directory`

**原因**: 项目已重构为多模块，JAR 路径已变更

**解决方案**:
```bash
# 重新构建
./quick-build.sh

# 检查新路径
ls -lh flink-jobs/target/flink-jobs-*.jar
ls -lh monitor-backend/target/monitor-backend-*.jar
```

### Q3: JobManager 不断重启

**错误**: `/docker-entrypoint.sh: No such file or directory`

**原因**: entrypoint 脚本问题

**解决方案**: 已修复，重新构建镜像
```bash
docker build -f docker/jobmanager/Dockerfile.cn -t flink-jobmanager:latest .
docker-compose up -d jobmanager
```

### Q4: TaskManager 无法连接到 JobManager

**检查清单**:
1. JobManager 是否正常运行
2. 网络是否正常（flink-network）
3. 端口是否冲突

```bash
# 检查网络
docker network ls | grep flink

# 检查端口
docker-compose ps

# 重启服务
docker-compose restart taskmanager
```

### Q5: CDC 任务没有恢复

**可能原因**:
1. Checkpoint 数据丢失
2. ZooKeeper 数据丢失
3. 任务配置变更

**解决方案**:
```bash
# 查看 JobManager 日志
docker logs flink-jobmanager | grep -i checkpoint

# 如果需要重新提交任务
# 通过 Monitor Backend API 或 Flink Web UI 提交
```

## 使用 start.sh 脚本

### 查看帮助

```bash
./start.sh --help
```

### 常用命令

```bash
# 查看状态
./start.sh --status

# 启动所有服务
./start.sh

# 重建 Flink 镜像并启动
./start.sh --rebuild-flink

# 清理并重新启动
./start.sh --clean

# 启动特定服务
./start.sh jobmanager
./start.sh taskmanager
./start.sh monitor-backend
```

## 服务访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| Flink Web UI | http://localhost:8081 | JobManager 控制台 |
| Flink Standby | http://localhost:8082 | 备用 JobManager |
| Monitor Backend | http://localhost:5001 | 监控后端 API |
| Monitor Frontend | http://localhost:8888 | 监控前端界面 |
| ZooKeeper | localhost:2181 | 高可用协调服务 |

## 数据目录

| 目录 | 说明 |
|------|------|
| `data/flink-checkpoints/` | Flink checkpoint 数据 |
| `data/zookeeper-data/` | ZooKeeper 数据 |
| `data/zookeeper-logs/` | ZooKeeper 日志 |
| `output/cdc/` | CDC 输出文件 |

## 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v

# 停止特定服务
docker-compose stop jobmanager
docker-compose stop taskmanager
```

## 清理环境

```bash
# 停止并删除所有容器和网络
docker-compose down

# 删除镜像
docker rmi flink-jobmanager:latest
docker rmi flink-taskmanager:latest

# 清理构建缓存
docker builder prune -a

# 清理 Maven 构建
mvn clean
```

## 监控和调试

### 查看资源使用

```bash
# 查看容器资源使用
docker stats

# 查看特定容器
docker stats flink-jobmanager flink-taskmanager-1
```

### 进入容器

```bash
# 进入 JobManager 容器
docker exec -it flink-jobmanager bash

# 进入 TaskManager 容器
docker exec -it flink-taskmanager-1 bash

# 查看 Flink 配置
docker exec flink-jobmanager cat /opt/flink/conf/flink-conf.yaml
```

### 查看 Flink 日志

```bash
# JobManager 日志
docker exec flink-jobmanager tail -f /opt/flink/log/flink-*-standalonesession-*.log

# TaskManager 日志
docker exec flink-taskmanager-1 tail -f /opt/flink/log/flink-*-taskexecutor-*.log
```

## 性能优化

### 调整 TaskManager 数量

```bash
# 扩展到 5 个 TaskManager
docker-compose up -d --scale taskmanager=5

# 缩减到 2 个 TaskManager
docker-compose up -d --scale taskmanager=2
```

### 调整内存配置

编辑 `docker-compose.yml`:

```yaml
jobmanager:
  environment:
    - JOB_MANAGER_HEAP_SIZE=2048m  # 增加 JobManager 内存

taskmanager:
  environment:
    - TASK_MANAGER_HEAP_SIZE=2048m  # 增加 TaskManager 内存
```

## 下一步

1. ✅ 完成 TaskManager 镜像构建
2. ✅ 启动所有服务
3. ✅ 验证 CDC 任务运行
4. 📊 监控任务性能
5. 🔧 根据需要调整配置

## 相关文档

- `PROJECT-RESTRUCTURE-SUMMARY.md` - 项目重构总结
- `STARTUP-FIX-SUMMARY.md` - 启动问题修复总结
- `BUILD-GUIDE.md` - 构建指南
- `START-GUIDE.md` - start.sh 使用指南
- `MULTI-MODULE-MIGRATION.md` - 多模块迁移文档
- `CHINA-MIRRORS.md` - 国内镜像源配置

## 技术支持

如有问题，请查看：
1. Docker 日志: `docker-compose logs -f`
2. Flink Web UI: http://localhost:8081
3. 相关文档（见上方列表）
