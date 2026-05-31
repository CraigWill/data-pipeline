# Docker 网络连接问题修复

## 问题描述

启动服务后遇到 `UnknownHostException: oracle11g` 错误：

```
Caused by: java.net.UnknownHostException: oracle11g
at java.base/java.net.InetAddress$CachedAddresses.get(Unknown Source)
at java.base/java.net.InetAddress.getAllByName0(Unknown Source)
...
```

## 根本原因

Docker 容器之间需要在同一网络中才能通过容器名互相访问。Oracle 数据库容器 `oracle11g` 和 Flink 服务容器不在同一网络中，导致无法解析主机名。

## 解决方案

### 场景 1: Oracle 数据库在 Docker 容器中（本项目情况）

#### 步骤 1: 检查 Oracle 容器是否运行

```bash
docker ps | grep oracle
```

预期输出：
```
oracle11g    akaiot/oracle_11g:latest    Up 2 hours
```

#### 步骤 2: 检查 Flink 网络是否存在

```bash
docker network ls | grep flink
```

预期输出：
```
flink-network    bridge    local
```

#### 步骤 3: 将 Oracle 容器连接到 Flink 网络

```bash
docker network connect flink-network oracle11g
```

#### 步骤 4: 验证连接

```bash
docker network inspect flink-network --format '{{range .Containers}}{{.Name}} {{end}}'
```

应该看到 `oracle11g` 在列表中。

#### 步骤 5: 重启依赖数据库的服务

```bash
docker-compose restart monitor-backend
```

#### 步骤 6: 验证服务启动成功

```bash
docker-compose logs -f monitor-backend
```

应该看到：
```
INFO  com.realtime.UnifiedApplication - Started UnifiedApplication in 3.808 seconds
INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Start completed.
```

### 场景 2: Oracle 数据库在宿主机上

如果 Oracle 数据库运行在宿主机（Mac/Windows/Linux）而不是 Docker 容器中：

#### 修改 `.env` 文件

```bash
# 使用特殊的主机名访问宿主机
DATABASE_HOST=host.docker.internal
```

#### 重启服务

```bash
docker-compose restart monitor-backend
```

### 场景 3: Oracle 数据库在远程服务器上

如果 Oracle 数据库在远程服务器上：

#### 修改 `.env` 文件

```bash
# 使用实际的 IP 地址或域名
DATABASE_HOST=192.168.1.100
# 或
DATABASE_HOST=oracle.example.com
```

#### 确保网络可达

```bash
# 从容器内测试连接
docker exec -it flink-monitor-backend ping oracle.example.com
```

## 自动化脚本

为了避免每次启动都需要手动连接网络，可以创建一个启动前检查脚本：

```bash
#!/bin/bash
# check-oracle-network.sh

ORACLE_CONTAINER="oracle11g"
FLINK_NETWORK="flink-network"

# 检查 Oracle 容器是否运行
if ! docker ps --format '{{.Names}}' | grep -q "^${ORACLE_CONTAINER}$"; then
    echo "警告: Oracle 容器 ${ORACLE_CONTAINER} 未运行"
    exit 1
fi

# 检查 Oracle 是否在 Flink 网络中
if ! docker network inspect ${FLINK_NETWORK} --format '{{range .Containers}}{{.Name}}{{"\n"}}{{end}}' | grep -q "^${ORACLE_CONTAINER}$"; then
    echo "将 ${ORACLE_CONTAINER} 连接到 ${FLINK_NETWORK}..."
    docker network connect ${FLINK_NETWORK} ${ORACLE_CONTAINER}
    echo "✓ 网络连接成功"
else
    echo "✓ ${ORACLE_CONTAINER} 已在 ${FLINK_NETWORK} 中"
fi
```

使用方法：

```bash
chmod +x check-oracle-network.sh
./check-oracle-network.sh
./start.sh
```

## 集成到 start.sh

可以将网络检查集成到 `start.sh` 中：

```bash
# 在 start.sh 的 main() 函数开始处添加
check_oracle_network() {
    local oracle_container="oracle11g"
    local flink_network="flink-network"
    
    # 检查 Oracle 容器是否运行
    if docker ps --format '{{.Names}}' | grep -q "^${oracle_container}$"; then
        # 检查是否在 Flink 网络中
        if ! docker network inspect ${flink_network} --format '{{range .Containers}}{{.Name}}{{"\n"}}{{end}}' 2>/dev/null | grep -q "^${oracle_container}$"; then
            echo -e "${YELLOW}将 ${oracle_container} 连接到 ${flink_network}...${NC}"
            docker network connect ${flink_network} ${oracle_container} 2>/dev/null || true
            echo -e "${GREEN}✓ 网络连接成功${NC}"
        fi
    fi
}

# 在 check_env 之后调用
check_oracle_network
```

## Docker Compose 配置（推荐方式）

更好的方式是在 `docker-compose.yml` 中定义 Oracle 服务，这样网络会自动配置：

```yaml
services:
  # 添加 Oracle 服务定义
  oracle11g:
    image: akaiot/oracle_11g:latest
    container_name: oracle11g
    ports:
      - "1521:1521"
    environment:
      - ORACLE_SID=helowin
      - ORACLE_PWD=oracle
    volumes:
      - oracle-data:/u01/app/oracle
    networks:
      - flink-network
    restart: unless-stopped

volumes:
  oracle-data:
    driver: local
```

这样启动时会自动处理网络配置：

```bash
docker-compose up -d
```

## 故障排查

### 问题 1: 仍然无法连接

```bash
# 测试容器间网络连通性
docker exec -it flink-monitor-backend ping oracle11g

# 测试端口连通性
docker exec -it flink-monitor-backend nc -zv oracle11g 1521
```

### 问题 2: 网络连接命令失败

```bash
# 错误: network already connected
# 解决: 容器已在网络中，无需操作

# 错误: network not found
# 解决: 先启动 Flink 服务创建网络
docker-compose up -d zookeeper
```

### 问题 3: DNS 解析失败

```bash
# 检查 Docker DNS 配置
docker exec -it flink-monitor-backend cat /etc/resolv.conf

# 重启 Docker 服务（macOS）
# Docker Desktop -> Restart
```

### 问题 4: 防火墙阻止连接

```bash
# macOS 检查防火墙
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate

# 临时禁用防火墙测试
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --setglobalstate off
```

## 验证清单

启动服务前检查：

- [ ] Oracle 容器正在运行
- [ ] Flink 网络已创建
- [ ] Oracle 容器在 Flink 网络中
- [ ] `.env` 文件中 `DATABASE_HOST` 配置正确
- [ ] 端口 1521 未被占用
- [ ] 防火墙允许容器间通信

## 网络架构图

```
┌─────────────────────────────────────────────────────────┐
│                    flink-network (bridge)                │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  zookeeper   │  │ jobmanager   │  │ taskmanager  │  │
│  │  :2181       │  │ :8081        │  │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │monitor-      │  │monitor-      │  │  oracle11g   │  │
│  │backend       │──│frontend      │  │  :1521       │  │
│  │:5001         │  │:80           │  │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│         │                                     ▲          │
│         └─────────────────────────────────────┘          │
│              通过容器名 "oracle11g" 访问                 │
└─────────────────────────────────────────────────────────┘
                          │
                          │ 端口映射
                          ▼
                    宿主机 (Mac)
              8888 → frontend
              5001 → backend
              8081 → jobmanager
              1521 → oracle
```

## 最佳实践

1. **使用 Docker Compose 管理所有服务**
   - 自动处理网络配置
   - 统一管理依赖关系
   - 简化启动流程

2. **使用容器名而非 IP 地址**
   - 容器重启后 IP 可能变化
   - 容器名在同一网络中始终可解析

3. **定义明确的网络拓扑**
   - 使用自定义网络而非默认网络
   - 隔离不同环境的服务

4. **健康检查**
   - 确保依赖服务启动后再启动应用
   - 使用 `depends_on` 和 `healthcheck`

5. **文档化网络配置**
   - 记录所有服务的网络依赖
   - 提供故障排查指南

## 相关文档

- [docker-compose.yml](./docker-compose.yml) - 服务定义和网络配置
- [.env](./.env) - 环境变量配置
- [START-SCRIPT-FIX.md](./START-SCRIPT-FIX.md) - 启动脚本修复指南
- [COMPLETE-STARTUP-GUIDE.md](./COMPLETE-STARTUP-GUIDE.md) - 完整启动指南

## 总结

Docker 网络问题的核心是确保需要通信的容器在同一网络中。通过 `docker network connect` 命令或在 `docker-compose.yml` 中定义网络，可以轻松解决容器间的连接问题。

对于本项目，已经通过以下命令修复：

```bash
docker network connect flink-network oracle11g
docker-compose restart monitor-backend
```

服务现在可以正常访问 Oracle 数据库了。
