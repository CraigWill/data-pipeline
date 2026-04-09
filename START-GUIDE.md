# 快速启动指南

## start.sh 使用说明

`start.sh` 是项目的统一启动脚本，支持构建私有 Flink 镜像和管理所有服务。

## 基本用法

### 1. 首次启动（完整构建）

```bash
./start.sh
```

这将：
1. 构建 Maven JAR 包
2. 构建 Flink 私有镜像（JobManager + TaskManager）
3. 构建其他 Docker 镜像
4. 启动所有服务
5. 显示服务状态

### 2. 快速启动（跳过构建）

```bash
./start.sh --skip-build
```

适用于镜像已构建，只需启动服务的场景。

### 3. 强制重建 Flink 镜像

```bash
./start.sh --rebuild-flink
```

使用 `--no-cache` 强制重新下载 Flink 并构建镜像，适用于：
- 更新 Flink 版本
- 修改 Dockerfile
- 镜像损坏需要重建

### 4. 只构建不启动

```bash
./start.sh --build-only
```

适用于 CI/CD 流程或预先构建镜像。

## 服务管理

### 启动特定服务

```bash
# 只启动后端
./start.sh backend

# 只启动前端
./start.sh frontend

# 只启动 Flink 集群
./start.sh flink

# 启动多个服务
./start.sh backend frontend
```

### 停止服务

```bash
# 停止所有服务
./start.sh --stop

# 停止特定服务
./start.sh --stop backend
```

### 重启服务

```bash
# 重启所有服务
./start.sh --restart

# 重启特定服务
./start.sh --restart flink
```

### 查看状态

```bash
./start.sh --status
```

输出：
- 所有容器状态
- 访问地址
- 镜像信息

### 查看日志

```bash
# 查看所有服务日志
./start.sh --logs

# 查看特定服务日志
./start.sh --logs backend
./start.sh --logs jobmanager
```

### 清理环境

```bash
./start.sh --clean
```

⚠️ 警告：这将删除所有容器、网络和数据卷！

## 服务别名

| 别名 | 实际服务 |
|------|----------|
| `all` | 所有服务（默认） |
| `backend` | monitor-backend |
| `frontend` | monitor-frontend |
| `flink` | jobmanager + jobmanager-standby + taskmanager |
| `jobmanager` | jobmanager + jobmanager-standby |
| `taskmanager` | taskmanager |
| `zookeeper` | zookeeper |

## 常见场景

### 场景 1: 开发调试

```bash
# 1. 首次启动
./start.sh

# 2. 修改代码后重新构建并重启后端
mvn clean package -DskipTests
docker-compose build monitor-backend
./start.sh --restart backend

# 3. 查看后端日志
./start.sh --logs backend
```

### 场景 2: 更新 Flink 配置

```bash
# 1. 修改 docker/jobmanager/flink-conf.yaml 或 Dockerfile

# 2. 重建 Flink 镜像
./start.sh --rebuild-flink

# 3. 查看 Flink 日志
./start.sh --logs jobmanager
```

### 场景 3: 前端开发

```bash
# 1. 只启动后端和 Flink
./start.sh backend flink

# 2. 在本地运行前端开发服务器
cd monitor/frontend-vue
npm run dev
```

### 场景 4: 生产部署

```bash
# 1. 构建镜像
./start.sh --build-only

# 2. 推送到私有仓库（使用 build-flink-images.sh）
./build-flink-images.sh v1.0.0 registry.company.com/project

# 3. 在生产服务器启动
./start.sh --skip-build
```

### 场景 5: 故障排查

```bash
# 1. 查看服务状态
./start.sh --status

# 2. 查看特定服务日志
./start.sh --logs jobmanager

# 3. 重启问题服务
./start.sh --restart jobmanager

# 4. 如果问题持续，清理并重建
./start.sh --clean
./start.sh --rebuild-flink
```

## 构建流程详解

### 完整构建流程

```
1. Maven 构建
   ├─ mvn clean package -DskipTests
   └─ 生成 target/realtime-data-pipeline-*.jar

2. Flink 镜像构建
   ├─ JobManager
   │  ├─ FROM openjdk:17-jre-slim
   │  ├─ 下载 Flink 1.20.0
   │  ├─ 下载 Oracle JDBC 驱动
   │  ├─ 复制 JAR 包和配置
   │  └─ 构建镜像 flink-jobmanager:latest
   │
   └─ TaskManager
      ├─ FROM openjdk:17-jre-slim
      ├─ 下载 Flink 1.20.0
      ├─ 下载 Oracle JDBC 驱动
      ├─ 复制 JAR 包和配置
      └─ 构建镜像 flink-taskmanager:latest

3. 其他镜像构建
   ├─ monitor-backend (Spring Boot)
   └─ monitor-frontend (Vue 3 + Nginx)

4. 启动服务
   └─ docker-compose up -d
```

### 增量构建

如果只修改了应用代码（不涉及 Flink 配置）：

```bash
# 1. 重新构建 JAR
mvn clean package -DskipTests

# 2. 重建包含应用的镜像
docker-compose build jobmanager taskmanager monitor-backend

# 3. 重启服务
./start.sh --restart
```

## 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- Maven 3.6+
- JDK 17+ (本地构建)
- 至少 8GB 可用内存
- 至少 20GB 可用磁盘空间

## 端口占用

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 8888 | Vue 3 前端界面 |
| 后端 | 5001 | Spring Boot API |
| Flink JobManager | 8081 | Flink Web UI |
| Flink JobManager 备用 | 8082 | 备用节点 Web UI |
| ZooKeeper | 2181 | 协调服务 |

## 故障排查

### 问题 1: Docker 构建失败

```bash
# 检查 Docker 状态
docker info

# 清理 Docker 缓存
docker system prune -a

# 重新构建
./start.sh --rebuild-flink
```

### 问题 2: Maven 构建失败

```bash
# 检查 Java 版本
java -version  # 应该是 17+

# 清理 Maven 缓存
mvn clean

# 重新构建
mvn clean package -DskipTests
```

### 问题 3: 服务启动失败

```bash
# 查看详细日志
./start.sh --logs

# 检查端口占用
lsof -i :8081
lsof -i :5001
lsof -i :8888

# 停止并清理
./start.sh --clean

# 重新启动
./start.sh
```

### 问题 4: Flink 下载慢

编辑 `docker/jobmanager/Dockerfile` 和 `docker/taskmanager/Dockerfile`，使用国内镜像：

```dockerfile
# 使用清华镜像
RUN wget -q https://mirrors.tuna.tsinghua.edu.cn/apache/flink/flink-${FLINK_VERSION}/...
```

## 性能优化

### 加速构建

```bash
# 使用 BuildKit
export DOCKER_BUILDKIT=1

# 并行构建
docker-compose build --parallel

# 使用构建缓存
# 不要使用 --no-cache 除非必要
```

### 减少镜像大小

在 Dockerfile 中：
- 删除不必要的文件
- 合并 RUN 命令
- 使用 .dockerignore

## 帮助信息

```bash
./start.sh --help
```

## 相关文档

- [FLINK-PRIVATE-IMAGE.md](./FLINK-PRIVATE-IMAGE.md) - Flink 私有镜像详细文档
- [PRIVATE-IMAGE-SUMMARY.md](./PRIVATE-IMAGE-SUMMARY.md) - 镜像迁移总结
- [build-flink-images.sh](./build-flink-images.sh) - 独立镜像构建脚本
- [docker-compose.yml](./docker-compose.yml) - 容器编排配置
