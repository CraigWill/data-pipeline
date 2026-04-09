# 快速启动指南

## 一键启动（3步完成）

### 步骤 1: 构建 Java 项目

```bash
./quick-build.sh
```

预期输出：
```
✓ Flink Jobs JAR: flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar 129M
✓ Monitor Backend JAR: monitor-backend/target/monitor-backend-1.0.0-SNAPSHOT.jar 89M
```

### 步骤 2: 构建 Docker 镜像

```bash
./rebuild-all.sh
```

预期输出：
```
✓ JobManager 镜像构建成功
✓ TaskManager 镜像构建成功
```

### 步骤 3: 启动所有服务

```bash
docker-compose up -d
```

预期输出：
```
✔ Container zookeeper                 Started
✔ Container flink-jobmanager          Started
✔ Container flink-jobmanager-standby  Started
✔ Container flink-taskmanager-1       Started
✔ Container flink-taskmanager-2       Started
✔ Container flink-taskmanager-3       Started
✔ Container flink-monitor-backend     Started
✔ Container flink-monitor-frontend    Started
```

## 验证服务

### 查看状态

```bash
./start.sh --status
```

或

```bash
docker-compose ps
```

### 访问服务

- **Flink Web UI**: http://localhost:8081
- **Monitor Backend**: http://localhost:5001
- **Monitor Frontend**: http://localhost:8888

### 查看日志

```bash
# 查看所有日志
docker-compose logs -f

# 只查看 Flink 日志
docker-compose logs -f jobmanager taskmanager
```

## 常见问题

### Q1: 构建失败 - JAR 文件不存在

**解决方案**: 先运行 `./quick-build.sh`

### Q2: Docker 镜像构建超时

**解决方案**: 
1. 检查网络连接
2. 重试构建
3. 使用标准版 Dockerfile（如果国内镜像源不可用）

### Q3: 容器启动失败

**解决方案**:
```bash
# 查看错误日志
docker-compose logs

# 重启服务
docker-compose restart

# 完全重建
docker-compose down
docker-compose up -d
```

### Q4: 端口冲突

**解决方案**: 检查端口是否被占用
```bash
# 检查端口
lsof -i :8081  # Flink Web UI
lsof -i :5001  # Monitor Backend
lsof -i :8888  # Monitor Frontend
lsof -i :2181  # ZooKeeper
```

## 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

## 清理环境

```bash
# 停止服务
docker-compose down

# 删除镜像
docker rmi flink-jobmanager:latest flink-taskmanager:latest

# 清理 Maven 构建
mvn clean
```

## 项目结构

```
realtime-data-pipeline-parent/
├── flink-jobs/              # Flink CDC 任务
│   ├── pom.xml
│   ├── src/
│   └── target/
│       └── flink-jobs-1.0.0-SNAPSHOT.jar
├── monitor-backend/         # Spring Boot 监控后端
│   ├── pom.xml
│   ├── src/
│   └── target/
│       └── monitor-backend-1.0.0-SNAPSHOT.jar
├── docker/                  # Docker 配置
│   ├── jobmanager/
│   │   ├── Dockerfile.cn    # 国内镜像源版本
│   │   └── entrypoint.sh
│   └── taskmanager/
│       ├── Dockerfile.cn    # 国内镜像源版本
│       └── entrypoint.sh
├── monitor/                 # Monitor Dockerfile
│   └── Dockerfile
├── docker-compose.yml       # Docker Compose 配置
├── quick-build.sh          # 快速构建脚本
├── rebuild-all.sh          # 重建所有镜像脚本
└── start.sh                # 启动脚本
```

## 技术栈

- **Java**: 11
- **Maven**: 3.x
- **Flink**: 1.20.0
- **Flink CDC**: 3.4.0
- **Spring Boot**: 2.7.18
- **Docker**: 最新版
- **Docker Compose**: 最新版

## 相关文档

- `COMPLETE-STARTUP-GUIDE.md` - 完整启动指南
- `PROJECT-RESTRUCTURE-SUMMARY.md` - 项目重构总结
- `BUILD-GUIDE.md` - 详细构建指南
- `STARTUP-FIX-SUMMARY.md` - 启动问题修复总结

## 下一步

1. ✅ 启动服务
2. 📊 访问 Flink Web UI 查看任务状态
3. 🔧 通过 Monitor Backend API 管理 CDC 任务
4. 📈 监控任务性能和输出

## 技术支持

如有问题，请查看：
1. Docker 日志: `docker-compose logs -f`
2. Flink Web UI: http://localhost:8081
3. 完整文档: `COMPLETE-STARTUP-GUIDE.md`
