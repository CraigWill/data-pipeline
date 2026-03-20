# Vue 3 前端部署指南

## 快速开始

### 方式 1: Docker Compose（推荐）

最简单的部署方式，一键启动所有服务。

```bash
# 1. 构建并启动所有服务
docker-compose up -d

# 2. 查看服务状态
docker-compose ps

# 3. 查看前端日志
docker-compose logs -f monitor-frontend

# 4. 访问应用
# Vue 3 前端: http://localhost:8888
# 原生前端: http://localhost:8889 (需要启用 legacy profile)
```

### 方式 2: 单独构建 Docker 镜像

如果只想构建前端镜像：

```bash
# 1. 进入前端目录
cd monitor/frontend-vue

# 2. 构建 Docker 镜像
docker build -t flink-monitor-vue:latest .

# 3. 运行容器
docker run -d \
  --name flink-monitor-frontend \
  -p 8888:80 \
  --network flink-network \
  flink-monitor-vue:latest

# 4. 查看日志
docker logs -f flink-monitor-frontend
```

### 方式 3: 本地开发

用于开发和调试：

```bash
# 1. 安装依赖
cd monitor/frontend-vue
npm install

# 2. 启动开发服务器
npm run dev

# 3. 访问 http://localhost:3000
```

### 方式 4: 手动构建部署

适合自定义部署环境：

```bash
# 1. 构建生产版本
cd monitor/frontend-vue
npm install
npm run build

# 2. 部署 dist 目录到 Web 服务器
# 例如 Nginx:
cp -r dist/* /usr/share/nginx/html/

# 3. 配置 Nginx（参考 nginx.conf）
```

## 部署架构

```
┌─────────────────────────────────────────────────────────┐
│                     Docker Network                       │
│                    (flink-network)                       │
│                                                          │
│  ┌──────────────┐      ┌──────────────┐                │
│  │   Vue 3      │      │   Monitor    │                │
│  │  Frontend    │─────▶│   Backend    │                │
│  │  (Nginx)     │ API  │ (Spring Boot)│                │
│  │  Port: 8888  │      │  Port: 5001  │                │
│  └──────────────┘      └──────────────┘                │
│         │                      │                         │
│         │                      ▼                         │
│         │              ┌──────────────┐                 │
│         │              │    Flink     │                 │
│         └─────────────▶│  JobManager  │                 │
│           (直接访问)    │  Port: 8081  │                 │
│                        └──────────────┘                 │
└─────────────────────────────────────────────────────────┘
```

## 环境变量配置

### Docker Compose 环境变量

在 `.env` 文件中配置：

```bash
# 后端 API 地址（容器内部使用）
BACKEND_API_URL=http://monitor-backend:5001

# Flink REST API 地址
FLINK_REST_URL=http://jobmanager:8081

# 时区
TZ=Asia/Shanghai
```

### Nginx 环境变量

Nginx 配置文件 `nginx.conf` 中的代理设置：

```nginx
location /api {
    proxy_pass http://monitor-backend:5001;
    # ... 其他配置
}
```

## 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| Vue 3 前端 | 8888 | 主前端应用 |
| 原生前端 | 8889 | 旧版前端（可选） |
| 后端 API | 5001 | Spring Boot 服务 |
| Flink UI | 8081 | Flink Web 界面 |

## 健康检查

### 前端健康检查

```bash
# Docker 健康检查
docker inspect flink-monitor-frontend | grep -A 10 Health

# 手动检查
curl http://localhost:8888/health
# 预期输出: healthy
```

### 完整服务检查

```bash
# 检查所有服务状态
docker-compose ps

# 检查前端
curl -I http://localhost:8888/

# 检查后端 API
curl http://localhost:5001/api/health

# 检查 Flink
curl http://localhost:8081/overview
```

## 故障排查

### 问题 1: 前端无法访问

```bash
# 1. 检查容器状态
docker-compose ps monitor-frontend

# 2. 查看日志
docker-compose logs monitor-frontend

# 3. 检查网络
docker network inspect flink-network

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

# 3. 检查 Nginx 配置
docker exec -it flink-monitor-frontend cat /etc/nginx/conf.d/default.conf
```

### 问题 3: 构建失败

```bash
# 1. 清理缓存
cd monitor/frontend-vue
rm -rf node_modules dist
npm cache clean --force

# 2. 重新安装依赖
npm install

# 3. 重新构建
npm run build

# 4. 检查 Node.js 版本
node -v  # 应该 >= 16
```

### 问题 4: 容器启动失败

```bash
# 1. 查看详细日志
docker-compose logs --tail=100 monitor-frontend

# 2. 检查镜像
docker images | grep monitor

# 3. 重新构建
docker-compose build --no-cache monitor-frontend

# 4. 重启服务
docker-compose restart monitor-frontend
```

## 性能优化

### 1. 启用 Gzip 压缩

已在 `nginx.conf` 中配置：

```nginx
gzip on;
gzip_types text/plain text/css application/javascript;
```

### 2. 静态资源缓存

```nginx
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}
```

### 3. 资源限制

在 `docker-compose.yml` 中已配置：

```yaml
deploy:
  resources:
    limits:
      cpus: '0.5'
      memory: 256M
```

## 更新部署

### 更新前端代码

```bash
# 1. 拉取最新代码
git pull

# 2. 重新构建镜像
docker-compose build monitor-frontend

# 3. 重启服务（零停机）
docker-compose up -d monitor-frontend

# 4. 验证更新
curl -I http://localhost:8888/
```

### 回滚到旧版本

```bash
# 1. 停止 Vue 3 前端
docker-compose stop monitor-frontend

# 2. 启动原生前端
docker-compose --profile legacy up -d monitor-frontend-legacy

# 3. 访问 http://localhost:8889
```

## 生产环境建议

### 1. 使用 HTTPS

```nginx
server {
    listen 443 ssl http2;
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    # ... 其他配置
}
```

### 2. 配置域名

```nginx
server {
    server_name monitor.example.com;
    # ... 其他配置
}
```

### 3. 添加访问控制

```nginx
location / {
    auth_basic "Restricted Access";
    auth_basic_user_file /etc/nginx/.htpasswd;
    # ... 其他配置
}
```

### 4. 日志管理

```bash
# 配置日志轮转
docker-compose logs --tail=1000 monitor-frontend > logs/frontend.log

# 或使用 Docker 日志驱动
# 在 docker-compose.yml 中添加:
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

### 5. 监控告警

```bash
# 使用 Prometheus + Grafana 监控
# 或使用云服务监控（阿里云、AWS CloudWatch 等）
```

## 备份与恢复

### 备份配置

```bash
# 备份配置文件
tar -czf frontend-config-backup.tar.gz \
  monitor/frontend-vue/nginx.conf \
  monitor/frontend-vue/vite.config.js \
  docker-compose.yml

# 备份镜像
docker save flink-monitor-vue:latest | gzip > frontend-image-backup.tar.gz
```

### 恢复

```bash
# 恢复配置
tar -xzf frontend-config-backup.tar.gz

# 恢复镜像
docker load < frontend-image-backup.tar.gz

# 重启服务
docker-compose up -d monitor-frontend
```

## 安全建议

1. **定期更新依赖**: `npm audit fix`
2. **使用最小权限**: 容器以非 root 用户运行
3. **启用 HTTPS**: 生产环境必须使用 SSL/TLS
4. **配置防火墙**: 限制端口访问
5. **日志审计**: 记录所有访问日志
6. **定期备份**: 自动化备份配置和数据

## 相关文档

- [Vue 3 官方文档](https://vuejs.org/)
- [Nginx 官方文档](https://nginx.org/en/docs/)
- [Docker 官方文档](https://docs.docker.com/)
- [项目 README](../../README.md)

## 支持

如有问题，请查看：
1. 项目 Issues
2. 日志文件
3. 健康检查状态
4. 网络连接

---

**最后更新**: 2026-03-09  
**版本**: 1.0.0
