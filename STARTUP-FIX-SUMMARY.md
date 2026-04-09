# 启动问题修复总结

## 问题描述

Docker 容器启动失败，JobManager 和 TaskManager 不断重启，错误信息：
```
/docker-entrypoint.sh: No such file or directory
```

## 根本原因

自定义构建的 Flink 镜像中，entrypoint.sh 脚本尝试调用不存在的 `/docker-entrypoint.sh`。这是因为我们从头构建镜像，没有官方 Flink 镜像的 entrypoint 脚本。

## 修复方案

### 1. 修复 JobManager entrypoint.sh

**文件**: `docker/jobmanager/entrypoint.sh`

**修改前**:
```bash
# 启动JobManager（后台运行）
/docker-entrypoint.sh jobmanager &
JOBMANAGER_PID=$!
```

**修改后**:
```bash
# 启动JobManager（后台运行）
# 直接使用 Flink 的启动脚本
$FLINK_HOME/bin/jobmanager.sh start-foreground &
JOBMANAGER_PID=$!
```

### 2. 修复 TaskManager entrypoint.sh

**文件**: `docker/taskmanager/entrypoint.sh`

**修改前**:
```bash
# 启动TaskManager
# 使用Flink官方的docker-entrypoint.sh
exec /docker-entrypoint.sh taskmanager
```

**修改后**:
```bash
# 启动TaskManager
# 直接使用 Flink 的启动脚本
exec $FLINK_HOME/bin/taskmanager.sh start-foreground
```

### 3. 更新 docker-compose.yml

将所有服务从 `build` 改为使用预构建的 `image`：

**修改前**:
```yaml
jobmanager:
  build:
    context: .
    dockerfile: docker/jobmanager/Dockerfile
```

**修改后**:
```yaml
jobmanager:
  image: flink-jobmanager:latest
```

同样修改：
- `jobmanager-standby`: 使用 `flink-jobmanager:latest`
- `taskmanager`: 使用 `flink-taskmanager:latest`

## 验证结果

### JobManager 启动成功 ✅

```bash
$ docker logs flink-jobmanager --tail 20
```

关键日志：
- ✅ JobManager 成功启动
- ✅ 连接到 ZooKeeper
- ✅ 从 checkpoint 恢复之前的 CDC 任务
- ✅ 任务状态切换到 RUNNING
- ⏳ 等待 TaskManager 连接

### 当前状态

```bash
$ docker-compose ps
```

- ✅ ZooKeeper: 运行中 (Healthy)
- ✅ JobManager: 运行中
- ⏳ TaskManager: 待构建镜像

## 下一步操作

### 1. 构建 TaskManager 镜像

TaskManager 镜像构建时遇到网络问题（apt-get 更新失败）。可以重试：

```bash
# 方法 1: 使用国内镜像源（推荐）
docker build -f docker/taskmanager/Dockerfile.cn -t flink-taskmanager:latest .

# 方法 2: 使用标准版本（如果网络稳定）
docker build -f docker/taskmanager/Dockerfile -t flink-taskmanager:latest .

# 方法 3: 使用构建脚本
./build-flink-images-cn.sh
```

### 2. 启动完整服务

```bash
# 启动所有服务
docker-compose up -d

# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f jobmanager taskmanager
```

### 3. 访问 Flink Web UI

```bash
# 打开浏览器访问
http://localhost:8081
```

应该能看到：
- JobManager 运行中
- TaskManager 已连接
- CDC 任务正在运行（从 checkpoint 恢复）

## 技术细节

### Flink 启动脚本

Flink 提供了两种启动方式：

1. **start-foreground**: 前台运行（推荐用于容器）
   ```bash
   $FLINK_HOME/bin/jobmanager.sh start-foreground
   $FLINK_HOME/bin/taskmanager.sh start-foreground
   ```

2. **start**: 后台运行（用于传统部署）
   ```bash
   $FLINK_HOME/bin/jobmanager.sh start
   $FLINK_HOME/bin/taskmanager.sh start
   ```

容器环境中必须使用 `start-foreground`，否则容器会立即退出。

### 为什么不使用官方镜像

我们从头构建镜像的原因：
1. 需要自定义 Flink 版本（1.20.0）
2. 需要预装 Oracle JDBC 驱动
3. 需要包含应用程序 JAR
4. 需要自定义配置和 entrypoint 逻辑

### Checkpoint 恢复

JobManager 启动时自动从 ZooKeeper 恢复：
- 发现之前的任务: `38afb1c662eb115413a7529fc3146bb0`
- 从 checkpoint 3433 恢复状态
- 任务名称: `cdc-no-duplicate`
- 等待 TaskManager 连接后继续执行

## 相关文件

- `docker/jobmanager/entrypoint.sh` - JobManager 启动脚本（已修复）
- `docker/taskmanager/entrypoint.sh` - TaskManager 启动脚本（已修复）
- `docker-compose.yml` - Docker Compose 配置（已更新）
- `build-flink-images-cn.sh` - 镜像构建脚本
- `PROJECT-RESTRUCTURE-SUMMARY.md` - 项目重构总结
- `BUILD-GUIDE.md` - 构建指南

## 故障排查

### 如果 JobManager 仍然失败

1. 检查镜像是否最新：
   ```bash
   docker images | grep flink-jobmanager
   ```

2. 强制重建镜像：
   ```bash
   docker-compose build --no-cache jobmanager
   ```

3. 查看详细日志：
   ```bash
   docker logs flink-jobmanager -f
   ```

### 如果 TaskManager 构建失败

1. 检查网络连接
2. 使用国内镜像源版本
3. 重试构建（网络问题通常是临时的）

## 总结

✅ 成功修复 entrypoint 脚本问题
✅ JobManager 正常启动并恢复任务
✅ 项目重构为多模块结构
⏳ 待完成：TaskManager 镜像构建和启动

系统已基本恢复正常，只需完成 TaskManager 镜像构建即可全面运行。
