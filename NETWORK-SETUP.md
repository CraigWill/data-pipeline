# Docker 网络配置说明

## 🔍 问题诊断

如果遇到 `java.sql.SQLRecoverableException: IO Error: The Network Adapter could not establish the connection` 错误，通常是因为 Docker 容器网络配置问题。

## 🌐 网络架构

### 当前配置

```
┌─────────────────────────────────────────────────────────┐
│ flink-network (Docker Bridge Network)                  │
│                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ JobManager   │  │ TaskManager  │  │ Monitor      │ │
│  │              │  │              │  │ Backend      │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
│         │                 │                  │         │
│         └─────────────────┴──────────────────┘         │
│                           │                            │
│                           │ DATABASE_HOST=oracle11g    │
│                           ↓                            │
│                  ┌──────────────┐                      │
│                  │ Oracle 11g   │                      │
│                  │ (oracle11g)  │                      │
│                  └──────────────┘                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 关键配置

1. **Oracle 容器必须在 flink-network 中**
2. **DATABASE_HOST 使用容器名 `oracle11g`**
3. **不使用 `host.docker.internal`**（虽然在 macOS 上可用，但容器间直接通信更高效）

## 🔧 配置步骤

### 1. 检查 Oracle 容器网络

```bash
docker inspect oracle11g | grep -A 10 "Networks"
```

应该看到 `flink-network` 在列表中。

### 2. 如果 Oracle 不在 flink-network

```bash
# 将 Oracle 容器连接到 flink-network
docker network connect flink-network oracle11g

# 验证
docker network inspect flink-network | grep oracle11g
```

### 3. 更新 .env 配置

确保 `.env` 文件中：

```bash
# 使用容器名
DATABASE_HOST=oracle11g

# 不要使用
# DATABASE_HOST=host.docker.internal  # ❌
# DATABASE_HOST=localhost              # ❌
```

### 4. 重启服务

```bash
# 重启 monitor-backend
docker-compose restart monitor-backend

# 或重启所有服务
docker-compose restart
```

## ✅ 验证连接

### 1. 检查服务状态

```bash
docker-compose ps
```

所有服务应该是 `Up` 和 `healthy` 状态。

### 2. 检查日志

```bash
# 查看 monitor-backend 日志
docker-compose logs monitor-backend --tail 50

# 应该看到 "Started UnifiedApplication"
```

### 3. 测试 API

```bash
# 健康检查
curl http://localhost:5001/api/health

# 应该返回 JSON 响应（即使是 Unauthorized 也说明服务正常）
```

### 4. 测试数据库连接

从容器内测试：

```bash
docker exec flink-monitor-backend sh -c "
  echo 'SELECT 1 FROM DUAL;' | \
  java -cp /app/lib/ojdbc8.jar oracle.jdbc.OracleDriver \
  jdbc:oracle:thin:@oracle11g:1521:helowin finance_user password
"
```

## 🐛 常见问题

### 问题 1: Network Adapter could not establish the connection

**原因：** Oracle 容器不在 flink-network 中

**解决：**
```bash
docker network connect flink-network oracle11g
docker-compose restart monitor-backend
```

### 问题 2: Unknown host 'oracle11g'

**原因：** DNS 解析失败，容器不在同一网络

**解决：**
```bash
# 检查网络
docker network inspect flink-network

# 确保 oracle11g 在网络中
docker network connect flink-network oracle11g
```

### 问题 3: Connection refused

**原因：** Oracle 服务未启动或端口未开放

**解决：**
```bash
# 检查 Oracle 容器状态
docker ps | grep oracle

# 检查端口
docker exec oracle11g netstat -tuln | grep 1521

# 重启 Oracle 容器
docker restart oracle11g
```

### 问题 4: Invalid username/password

**原因：** 密码错误或用户不存在

**解决：**
```bash
# 检查 .env 文件
cat .env | grep DATABASE

# 测试连接
docker exec oracle11g bash -c "
  source /home/oracle/.bash_profile
  sqlplus finance_user/password@helowin
"
```

## 📊 网络诊断命令

### 检查容器网络

```bash
# 列出所有网络
docker network ls

# 检查 flink-network
docker network inspect flink-network

# 检查 Oracle 容器网络
docker inspect oracle11g | grep -A 20 "Networks"
```

### 测试容器间连接

```bash
# 从 monitor-backend 容器 ping Oracle
docker exec flink-monitor-backend ping -c 3 oracle11g

# 从 monitor-backend 容器测试 Oracle 端口
docker exec flink-monitor-backend nc -zv oracle11g 1521
```

### 查看容器 IP

```bash
# Oracle 容器 IP
docker inspect oracle11g | grep IPAddress

# monitor-backend 容器 IP
docker inspect flink-monitor-backend | grep IPAddress
```

## 🔄 完整重置流程

如果问题持续，可以完全重置网络配置：

```bash
# 1. 停止所有服务
docker-compose down

# 2. 删除网络
docker network rm flink-network

# 3. 重新创建网络
docker network create flink-network

# 4. 将 Oracle 连接到网络
docker network connect flink-network oracle11g

# 5. 启动服务
docker-compose up -d

# 6. 检查状态
docker-compose ps
docker-compose logs monitor-backend --tail 50
```

## 📝 最佳实践

### 1. 使用容器名而不是 IP

✅ **推荐：**
```bash
DATABASE_HOST=oracle11g
```

❌ **不推荐：**
```bash
DATABASE_HOST=172.18.0.5  # IP 可能会变
```

### 2. 使用自定义网络

✅ **推荐：**
```yaml
networks:
  flink-network:
    driver: bridge
```

❌ **不推荐：**
```yaml
# 使用默认 bridge 网络
```

### 3. 明确定义网络

在 `docker-compose.yml` 中：

```yaml
services:
  monitor-backend:
    networks:
      - flink-network

networks:
  flink-network:
    external: true  # 使用已存在的网络
```

### 4. 健康检查

添加健康检查确保服务就绪：

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:5001/api/health"]
  interval: 30s
  timeout: 10s
  retries: 3
```

## 🆘 获取帮助

如果问题仍未解决：

1. **收集诊断信息：**
   ```bash
   docker network ls > network-info.txt
   docker network inspect flink-network >> network-info.txt
   docker inspect oracle11g >> network-info.txt
   docker-compose logs monitor-backend >> network-info.txt
   ```

2. **检查防火墙：**
   ```bash
   # macOS
   sudo pfctl -s all
   
   # Linux
   sudo iptables -L
   ```

3. **查看 Docker 日志：**
   ```bash
   # macOS
   tail -f ~/Library/Containers/com.docker.docker/Data/log/vm/dockerd.log
   ```

---

**最后更新：** 2026-04-10  
**版本：** 1.0
