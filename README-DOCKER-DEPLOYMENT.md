# Vue 3 前端 Docker 部署快速指南

## 🚀 快速开始（3 步完成部署）

### 步骤 1: 运行部署脚本

```bash
./deploy-vue-frontend.sh
```

选择选项 1（完整部署）

### 步骤 2: 等待服务启动

大约需要 30-60 秒，脚本会自动完成：
- 构建 Vue 3 前端镜像
- 启动所有服务（Zookeeper、Flink、后端、前端）
- 健康检查

### 步骤 3: 访问应用

打开浏览器访问：
- **Vue 3 前端**: http://localhost:8888
- **Flink UI**: http://localhost:8081
- **后端 API**: http://localhost:5001

## 📋 部署选项

### 选项 1: 完整部署（推荐）
```bash
./deploy-vue-frontend.sh
# 选择: 1
```
启动所有服务，包括 Zookeeper、Flink、后端和前端。

### 选项 2: 仅部署前端
```bash
./deploy-vue-frontend.sh
# 选择: 2
```
只构建和启动 Vue 3 前端，适合前端开发。

### 选项 3: 重新构建
```bash
./deploy-vue-frontend.sh
# 选择: 3
```
清理旧镜像，重新构建前端，适合代码更新后。

### 选项 4: 查看状态
```bash
./deploy-vue-frontend.sh
# 选择: 4
```
查看所有服务运行状态和健康检查。

### 选项 5: 查看日志
```bash
./deploy-vue-frontend.sh
# 选择: 5
```
实时查看前端日志，按 Ctrl+C 退出。

### 选项 6: 停止服务
```bash
./deploy-vue-frontend.sh
# 选择: 6
```
停止所有服务。

## 🧪 测试部署

运行测试脚本验证部署：

```bash
./test-deployment.sh
```

测试内容：
- ✓ Docker 服务状态
- ✓ 容器运行状态
- ✓ 服务端点可访问性
- ✓ 前端页面路由
- ✓ API 端点响应
- ✓ 健康检查状态

## 📦 手动部署（高级）

### 使用 Docker Compose

```bash
# 启动所有服务
docker-compose up -d

# 仅启动前端
docker-compose up -d monitor-frontend

# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f monitor-frontend

# 停止服务
docker-compose down
```

### 使用 Docker 命令

```bash
# 1. 构建镜像
cd monitor/frontend-vue
docker build -t flink-monitor-vue:latest .

# 2. 运行容器
docker run -d \
  --name flink-monitor-frontend \
  -p 8888:80 \
  --network flink-network \
  flink-monitor-vue:latest

# 3. 查看日志
docker logs -f flink-monitor-frontend

# 4. 停止容器
docker stop flink-monitor-frontend
docker rm flink-monitor-frontend
```

## 🔧 常见问题

### Q1: 端口被占用

```bash
# 查看端口占用
lsof -i :8888

# 修改端口（编辑 docker-compose.yml）
ports:
  - "9999:80"  # 改为其他端口
```

### Q2: 构建失败

```bash
# 清理 Docker 缓存
docker system prune -a

# 重新构建
docker-compose build --no-cache monitor-frontend
```

### Q3: 前端无法访问后端

```bash
# 检查网络
docker network inspect flink-network

# 检查后端服务
docker-compose ps monitor-backend
docker-compose logs monitor-backend
```

### Q4: 页面显示空白

```bash
# 检查前端日志
docker-compose logs monitor-frontend

# 检查 Nginx 配置
docker exec -it flink-monitor-frontend cat /etc/nginx/conf.d/default.conf

# 进入容器检查文件
docker exec -it flink-monitor-frontend sh
ls -la /usr/share/nginx/html
```

## 📊 服务架构

```
┌─────────────────────────────────────────┐
│         Docker Compose 服务              │
├─────────────────────────────────────────┤
│                                          │
│  Vue 3 Frontend (Port 8888)             │
│         ↓ API 代理                       │
│  Monitor Backend (Port 5001)            │
│         ↓ REST API                       │
│  Flink JobManager (Port 8081)           │
│         ↓ HA 协调                        │
│  Zookeeper (Port 2181)                  │
│                                          │
└─────────────────────────────────────────┘
```

## 📝 配置文件

| 文件 | 说明 |
|------|------|
| `docker-compose.yml` | Docker Compose 配置 |
| `monitor/frontend-vue/Dockerfile` | 前端镜像定义 |
| `monitor/frontend-vue/nginx.conf` | Nginx 配置 |
| `monitor/frontend-vue/.dockerignore` | 构建忽略文件 |
| `.env` | 环境变量配置 |

## 🔐 生产环境建议

1. **启用 HTTPS**
   ```nginx
   listen 443 ssl http2;
   ssl_certificate /etc/nginx/ssl/cert.pem;
   ssl_certificate_key /etc/nginx/ssl/key.pem;
   ```

2. **配置域名**
   ```nginx
   server_name monitor.example.com;
   ```

3. **添加访问控制**
   ```nginx
   auth_basic "Restricted Access";
   auth_basic_user_file /etc/nginx/.htpasswd;
   ```

4. **配置日志轮转**
   ```yaml
   logging:
     driver: "json-file"
     options:
       max-size: "10m"
       max-file: "3"
   ```

5. **资源监控**
   - 使用 Prometheus + Grafana
   - 配置告警规则
   - 定期备份数据

## 📚 详细文档

- [完整部署文档](DOCKER-DEPLOYMENT.md)
- [Vue 3 前端部署指南](monitor/frontend-vue/DEPLOYMENT.md)
- [Vue 3 迁移报告](monitor/frontend-vue/VUE-MIGRATION-COMPLETE.md)
- [项目 README](README.md)

## 🆘 获取帮助

1. 查看日志: `docker-compose logs -f`
2. 检查状态: `docker-compose ps`
3. 运行测试: `./test-deployment.sh`
4. 查看文档: `cat DOCKER-DEPLOYMENT.md`

## ✅ 部署检查清单

- [ ] Docker 和 Docker Compose 已安装
- [ ] 端口 8888、5001、8081 未被占用
- [ ] 有足够的磁盘空间（至少 5GB）
- [ ] 网络连接正常
- [ ] 已配置 .env 文件（可选）
- [ ] 运行 `./deploy-vue-frontend.sh`
- [ ] 运行 `./test-deployment.sh` 验证
- [ ] 访问 http://localhost:8888 确认

---

**部署完成！** 🎉

如有问题，请查看详细文档或运行测试脚本。
