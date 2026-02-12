# Docker Compose 快速开始指南

本指南帮助您快速启动实时数据管道系统。

## 前置条件

- Docker 20.10+
- Docker Compose 2.0+
- Maven 3.6+
- Java 11+

## 快速开始（5分钟）

### 1. 构建应用

```bash
# 克隆仓库
git clone <repository-url>
cd realtime-data-pipeline

# 构建应用
mvn clean package -DskipTests
```

### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑配置（必需）
vim .env
```

**最小配置（必需修改）:**
```bash
# 数据库配置
DATABASE_HOST=your-oceanbase-host
DATABASE_USERNAME=your-username
DATABASE_PASSWORD=your-password
DATABASE_SCHEMA=your-schema

# DataHub配置
DATAHUB_ENDPOINT=https://dh-cn-hangzhou.aliyuncs.com
DATAHUB_ACCESS_ID=your-access-id
DATAHUB_ACCESS_KEY=your-access-key
DATAHUB_PROJECT=your-project
DATAHUB_TOPIC=your-topic
```

### 3. 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 4. 验证部署

```bash
# 访问Flink Web UI
open http://localhost:8081

# 检查健康状态
curl http://localhost:8080/health

# 查看指标
curl http://localhost:8080/metrics
```

## 常用命令

### 服务管理

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f [service-name]
```

### 扩展TaskManager

```bash
# 扩展到3个TaskManager
docker-compose up -d --scale taskmanager=3

# 缩减到1个TaskManager
docker-compose up -d --scale taskmanager=1
```

### 启用高可用

```bash
# 修改.env文件
echo "HA_MODE=zookeeper" >> .env
echo "HA_ZOOKEEPER_QUORUM=zookeeper:2181" >> .env

# 启动HA模式
docker-compose --profile ha up -d
```

## 故障排查

### 服务无法启动

```bash
# 查看日志
docker-compose logs <service-name>

# 检查配置
docker-compose config

# 验证环境变量
cat .env
```

### 健康检查失败

```bash
# 查看健康状态
docker ps

# 查看健康检查日志
docker inspect <container-name> | jq '.[0].State.Health'

# 手动测试健康检查
docker exec <container-name> curl -f http://localhost:8081/overview
```

### 服务间无法通信

```bash
# 检查网络
docker network inspect flink-network

# 测试连接
docker exec taskmanager ping jobmanager
```

## 下一步

- 查看 [完整文档](../docs/TASK_14.4_DOCKER_COMPOSE.md)
- 查看 [健康检查配置](../docs/TASK_14.3_HEALTH_CHECK_CONFIG.md)
- 查看 [Docker部署指南](README.md)
- 查看 [主README](../README.md)

## 获取帮助

如果遇到问题：

1. 查看日志: `docker-compose logs -f`
2. 检查配置: `docker-compose config`
3. 查看文档: `docs/TASK_14.4_DOCKER_COMPOSE.md`
4. 提交Issue: <repository-url>/issues
