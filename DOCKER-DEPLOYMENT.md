# Docker 部署完成文档

## 概述

Vue 3 前端已成功配置 Docker 部署，支持多种部署方式。

## 部署文件清单

### 新增文件

1. **monitor/frontend-vue/Dockerfile** - Vue 3 前端 Docker 镜像定义
2. **monitor/frontend-vue/nginx.conf** - Nginx 配置文件
3. **monitor/frontend-vue/.dockerignore** - Docker 构建忽略文件
4. **monitor/frontend-vue/build.sh** - 本地构建脚本
5. **monitor/frontend-vue/DEPLOYMENT.md** - 详细部署文档
6. **deploy-vue-frontend.sh** - 快速部署脚本

### 修改文件

1. **docker-compose.yml** - 更新前端服务配置

## 快速开始

### 方式 1: 使用快速部署脚本（推荐）

```bash
# 运行部署脚本
./deploy-vue-frontend.sh

# 选择部署选项：
# 1) 完整部署（所有服务）
# 2) 仅部署前端
# 3) 重新构建前端
# 4) 查看服务状态
# 5) 查看前端日志
# 6) 停止所有服务
```

### 方式 2: 使用 Docker Compose

```bash
# 1. 构建并启动所有服务
docker-compose up -d

# 2. 仅启动前端
docker-compose up -d monitor-frontend

# 3. 查看状态
docker-compose ps

# 4. 查看日志
docker-compose logs -f monitor-frontend

# 5. 停止服务
docker-compose down
```

### 方式 3: 手动构建

```bash
# 1. 构建前端
cd monitor/frontend-vue
./build.sh

# 2. 构建 Docker 镜像
docker build -t flink-monitor-vue:latest .

# 3. 运行容器
docker run -d \
  --name flink-monitor-frontend \
  -p 8888:80 \
  --network flink-network \
  flink-monitor-vue:latest
```

## 服务架构

```
┌─────────────────────────────────────────────────────────┐
│                  Docker Compose 服务                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────────┐      ┌──────────────────┐        │
│  │  Vue 3 Frontend  │      │  Legacy Frontend │        │
│  │   (Nginx:80)     │      │   (Nginx:80)     │        │
│  │   Port: 8888     │      │   Port: 8889     │        │
│  │   [默认启用]      │      │   [可选启用]      │        │
│  └────────┬─────────┘      └──────────────────┘        │
│           │                                              │
│           │ API 代理                                     │
│           ▼                                              │
│  ┌──────────────────┐                                   │
│  │ Monitor Backend  │                                   │
│  │ (Spring Boot)    │                                   │
│  │   Port: 5001     │                                   │
│  └────────┬─────────┘                                   │
│           │                                              │
│           │ REST API                                     │
│           ▼                                              │
│  ┌──────────────────┐      ┌──────────────────┐        │
│  │ Flink JobManager │◄────►│ Flink TaskManager│        │
│  │   Port: 8081     │      │   (可扩展)        │        │
│  └──────────────────┘      └──────────────────┘        │
│           │                                              │
│           │ HA 协调                                      │
│           ▼                                              │
│  ┌──────────────────┐                                   │
│  │    Zookeeper     │                                   │
│  │   Port: 2181     │                                   │
│  └──────────────────┘                                   │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## 端口映射

| 服务 | 容器端口 | 主机端口 | 说明 |
|------|---------|---------|------|
| Vue 3 前端 | 80 | 8888 | 主前端应用 |
| 原生前端 | 80 | 8889 | 旧版前端（可选） |
| 后端 API | 5001 | 5001 | Spring Boot 服务 |
| Flink UI | 8081 | 8081 | Flink Web 界面 |
| Zookeeper | 2181 | 2181 | HA 协调服务 |

## 访问地址

部署完成后，可以通过以下地址访问：

- **Vue 3 前端**: http://localhost:8888
- **原生前端**: http://localhost:8889 (需启用 legacy profile)
- **后端 API**: http://localhost:5001/api
- **Flink UI**: http://localhost:8081

## Docker Compose 配置详解

### Vue 3 前端服务

```yaml
monitor-frontend:
  build:
    context: ./monitor/frontend-vue
    dockerfile: Dockerfile
  container_name: flink-monitor-frontend
  depends_on:
    - monitor-backend
  ports:
    - "8888:80"
  networks:
    - flink-network
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 10s
  deploy:
    resources:
      limits:
        cpus: '0.5'
        memory: 256M
```

### 原生前端服务（可选）

```yaml
monitor-frontend-legacy:
  image: nginx:alpine
  container_name: flink-monitor-frontend-legacy
  ports:
    - "8889:80"
  profiles:
    - legacy  # 需要显式启用
```

启用原生前端：
```bash
docker-compose --profile legacy up -d monitor-frontend-legacy
```

## Dockerfile 说明

### 多阶段构建

```dockerfile
# 阶段 1: 构建 Vue 应用
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
RUN npm run build

# 阶段 2: Nginx 运行环境
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### 优势

1. **体积小**: 最终镜像只包含构建产物和 Nginx
2. **安全**: 不包含源代码和开发依赖
3. **快速**: 利用 Docker 缓存加速构建

## Nginx 配置说明

### 关键配置

```nginx
# Vue Router History 模式支持
location / {
    try_files $uri $uri/ /index.html;
}

# API 代理
location /api {
    proxy_pass http://monitor-backend:5001;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}

# 静态资源缓存
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}

# Gzip 压缩
gzip on;
gzip_types text/plain text/css application/javascript;
```

## 健康检查

### 前端健康检查

```bash
# Docker 健康检查
docker inspect flink-monitor-frontend | grep -A 10 Health

# 手动检查
curl http://localhost:8888/health
# 输出: healthy
```

### 所有服务健康检查

```bash
# 检查所有服务
docker-compose ps

# 前端
curl -I http://localhost:8888/

# 后端
curl http://localhost:5001/api/health

# Flink
curl http://localhost:8081/overview
```

## 常见操作

### 查看日志

```bash
# 实时日志
docker-compose logs -f monitor-frontend

# 最近 100 行
docker-compose logs --tail=100 monitor-frontend

# 所有服务日志
docker-compose logs -f
```

### 重启服务

```bash
# 重启前端
docker-compose restart monitor-frontend

# 重启所有服务
docker-compose restart
```

### 更新前端

```bash
# 1. 拉取最新代码
git pull

# 2. 重新构建
docker-compose build monitor-frontend

# 3. 重启服务
docker-compose up -d monitor-frontend
```

### 扩展 TaskManager

```bash
# 扩展到 3 个 TaskManager
docker-compose up -d --scale taskmanager=3

# 查看状态
docker-compose ps taskmanager
```

## 故障排查

### 问题 1: 前端无法访问

```bash
# 1. 检查容器状态
docker-compose ps monitor-frontend

# 2. 查看日志
docker-compose logs monitor-frontend

# 3. 检查端口
netstat -an | grep 8888

# 4. 进入容器检查
docker exec -it flink-monitor-frontend sh
wget -O- http://localhost/health
```

### 问题 2: API 请求失败

```bash
# 1. 检查后端服务
docker-compose ps monitor-backend

# 2. 测试后端连接
docker exec -it flink-monitor-frontend sh
wget -O- http://monitor-backend:5001/api/health

# 3. 检查网络
docker network inspect flink-network
```

### 问题 3: 构建失败

```bash
# 1. 清理缓存
docker-compose build --no-cache monitor-frontend

# 2. 查看构建日志
docker-compose build monitor-frontend 2>&1 | tee build.log

# 3. 检查磁盘空间
df -h
docker system df
```

### 问题 4: 容器频繁重启

```bash
# 1. 查看重启次数
docker inspect flink-monitor-frontend | grep RestartCount

# 2. 查看退出代码
docker inspect flink-monitor-frontend | grep ExitCode

# 3. 查看详细日志
docker logs --tail=200 flink-monitor-frontend
```

## 性能优化

### 1. 构建优化

```bash
# 使用 .dockerignore 减少构建上下文
# 已配置在 monitor/frontend-vue/.dockerignore
```

### 2. 运行时优化

```yaml
# 资源限制（已配置）
deploy:
  resources:
    limits:
      cpus: '0.5'
      memory: 256M
```

### 3. 网络优化

```nginx
# Gzip 压缩（已配置）
gzip on;
gzip_types text/plain text/css application/javascript;

# 静态资源缓存（已配置）
expires 1y;
add_header Cache-Control "public, immutable";
```

## 生产环境建议

### 1. 使用环境变量

```bash
# 创建 .env 文件
cp .env.example .env

# 编辑配置
vim .env
```

### 2. 启用 HTTPS

```nginx
server {
    listen 443 ssl http2;
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
}
```

### 3. 配置日志轮转

```yaml
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

### 4. 使用 Docker Swarm 或 Kubernetes

```bash
# Docker Swarm
docker stack deploy -c docker-compose.yml flink-cdc

# Kubernetes
# 需要转换 docker-compose.yml 为 k8s manifests
```

## 备份与恢复

### 备份

```bash
# 备份配置
tar -czf config-backup.tar.gz \
  docker-compose.yml \
  .env \
  monitor/frontend-vue/nginx.conf

# 备份镜像
docker save flink-monitor-vue:latest | gzip > frontend-image.tar.gz

# 备份数据卷
docker run --rm -v flink-checkpoints:/data -v $(pwd):/backup \
  alpine tar czf /backup/checkpoints-backup.tar.gz /data
```

### 恢复

```bash
# 恢复配置
tar -xzf config-backup.tar.gz

# 恢复镜像
docker load < frontend-image.tar.gz

# 恢复数据卷
docker run --rm -v flink-checkpoints:/data -v $(pwd):/backup \
  alpine tar xzf /backup/checkpoints-backup.tar.gz -C /
```

## 监控和告警

### Prometheus 指标

```yaml
# 在 docker-compose.yml 中添加
prometheus:
  image: prom/prometheus
  ports:
    - "9090:9090"
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml
```

### Grafana 仪表盘

```yaml
grafana:
  image: grafana/grafana
  ports:
    - "3000:3000"
  depends_on:
    - prometheus
```

## 相关文档

- [Vue 3 前端详细部署文档](monitor/frontend-vue/DEPLOYMENT.md)
- [Vue 3 迁移完成报告](monitor/frontend-vue/VUE-MIGRATION-COMPLETE.md)
- [项目 README](README.md)

## 总结

Vue 3 前端已成功配置 Docker 部署，具备以下特性：

✅ 多阶段构建，镜像体积小  
✅ Nginx 优化配置，性能好  
✅ 健康检查，自动重启  
✅ 资源限制，稳定运行  
✅ 日志管理，便于调试  
✅ 快速部署脚本，操作简单  

---

**部署完成日期**: 2026-03-09  
**版本**: 1.0.0  
**状态**: ✅ 就绪
