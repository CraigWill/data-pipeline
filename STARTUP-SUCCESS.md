# 🎉 启动成功！

## 系统状态

所有服务已成功启动并运行正常：

```
✅ zookeeper                 - 健康 (端口 2181)
✅ flink-jobmanager          - 健康 (端口 8081)
✅ flink-jobmanager-standby  - 健康 (端口 8082)
✅ flink-taskmanager (x2)    - 健康
✅ monitor-backend           - 健康 (端口 5001)
✅ monitor-frontend          - 健康 (端口 8888)
✅ oracle11g                 - 运行中 (端口 1521)
```

## 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| **前端界面** | http://localhost:8888 | Vue 3 监控界面 |
| **后端 API** | http://localhost:5001 | Spring Boot REST API |
| **Flink 主节点** | http://localhost:8081 | Flink JobManager Web UI |
| **Flink 备用节点** | http://localhost:8082 | Flink Standby JobManager |
| **健康检查** | http://localhost:5001/actuator/health | 后端健康状态 |

## 默认登录凭据

```
用户名: admin
密码: admin123
```

**⚠️ 生产环境请立即修改默认密码！**

## 已解决的问题

### 1. Docker Hub 连接超时 ✅
- **问题**: 无法从 Docker Hub 拉取镜像
- **解决**: 自动使用国内镜像源 (Dockerfile.cn)
- **文档**: [START-SCRIPT-FIX.md](./START-SCRIPT-FIX.md)

### 2. Docker BuildKit Cache Key 错误 ✅
- **问题**: BuildKit 无法计算文件校验和
- **解决**: 禁用 BuildKit，使用传统构建器
- **修改**: `start.sh` 中添加 `DOCKER_BUILDKIT=0`

### 3. Oracle 数据库网络连接 ✅
- **问题**: `UnknownHostException: oracle11g`
- **解决**: 将 Oracle 容器连接到 flink-network
- **命令**: `docker network connect flink-network oracle11g`
- **文档**: [DOCKER-NETWORK-FIX.md](./DOCKER-NETWORK-FIX.md)

### 4. 前端健康检查失败 ✅
- **问题**: wget 无法连接到 localhost
- **解决**: 改用 curl 并使用 127.0.0.1:80
- **修改**: `docker-compose.yml` 健康检查配置

## 快速命令

### 查看服务状态
```bash
docker-compose ps
```

### 查看日志
```bash
# 所有服务
docker-compose logs -f

# 特定服务
docker-compose logs -f monitor-backend
docker-compose logs -f jobmanager
```

### 重启服务
```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart monitor-backend
```

### 停止服务
```bash
docker-compose down
```

### 完全清理（包括数据卷）
```bash
docker-compose down -v
```

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    flink-network (bridge)                    │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  zookeeper   │  │ jobmanager   │  │jobmanager-   │      │
│  │  :2181       │  │ :8081        │  │standby :8082 │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│         │                  │                  │              │
│         └──────────────────┴──────────────────┘              │
│                            │                                 │
│  ┌──────────────┐  ┌──────────────┐                         │
│  │taskmanager-1 │  │taskmanager-2 │                         │
│  │              │  │              │                         │
│  └──────────────┘  └──────────────┘                         │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │monitor-      │  │monitor-      │  │  oracle11g   │      │
│  │backend       │──│frontend      │  │  :1521       │      │
│  │:5001         │  │:8888         │  │              │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│         │                                     ▲              │
│         └─────────────────────────────────────┘              │
│              JDBC 连接到 Oracle 数据库                       │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ 端口映射
                          ▼
                    宿主机 (Mac)
              8888 → 前端界面
              5001 → 后端 API
              8081 → Flink 主节点
              8082 → Flink 备用节点
              1521 → Oracle 数据库
```

## 功能验证

### 1. 访问前端界面
```bash
open http://localhost:8888
```

### 2. 测试后端 API
```bash
# 健康检查
curl http://localhost:5001/actuator/health

# 登录
curl -X POST http://localhost:5001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 获取集群概览
curl http://localhost:5001/api/cluster/overview
```

### 3. 访问 Flink Web UI
```bash
open http://localhost:8081
```

### 4. 检查数据库连接
```bash
# 从 monitor-backend 容器测试
docker exec -it flink-monitor-backend \
  sh -c 'echo "SELECT 1 FROM DUAL;" | sqlplus -S finance_user/[password]@oracle11g:1521/helowin'
```

## 下一步操作

### 1. 创建 CDC 任务

访问前端界面 → 数据源管理 → 添加数据源：

```
数据源名称: Oracle CDC
主机: oracle11g
端口: 1521
SID: helowin
用户名: finance_user
密码: [your-password]
Schema: FINANCE_USER
表: TRANS_INFO
```

### 2. 提交 Flink 作业

方式 1: 通过前端界面
- 访问 http://localhost:8888/jobs
- 点击"提交作业"
- 选择 JAR 文件和配置参数

方式 2: 通过 Flink Web UI
- 访问 http://localhost:8081
- Submit New Job → Upload JAR
- 选择 `/opt/flink/usrlib/flink-jobs.jar`

方式 3: 通过命令行
```bash
docker exec -it flink-jobmanager \
  /opt/flink/bin/flink run \
  /opt/flink/usrlib/flink-jobs.jar
```

### 3. 监控 CDC 事件

访问前端界面 → CDC 任务 → 查看实时事件流

或通过 API：
```bash
curl http://localhost:5001/api/cdc/events/stream
```

### 4. 查看输出数据

CDC 事件输出到：
```bash
ls -lh ./output/cdc/
```

## 性能优化建议

### 1. 调整 TaskManager 数量

```bash
# 扩展到 4 个 TaskManager
docker-compose up -d --scale taskmanager=4
```

### 2. 调整内存配置

编辑 `.env` 文件：
```bash
JOB_MANAGER_HEAP_SIZE=2048m
TASK_MANAGER_HEAP_SIZE=2048m
```

重启服务：
```bash
docker-compose restart jobmanager taskmanager
```

### 3. 调整 Checkpoint 间隔

编辑 `.env` 文件：
```bash
# 从 5 分钟改为 1 分钟
CHECKPOINT_INTERVAL=60000
```

### 4. 启用 RocksDB 状态后端（大状态）

编辑 `.env` 文件：
```bash
STATE_BACKEND=rocksdb
```

## 故障排查

### 服务无法启动

```bash
# 查看详细日志
docker-compose logs -f [service-name]

# 检查端口占用
lsof -i :8081
lsof -i :8888
lsof -i :5001

# 重新构建镜像
./start.sh --rebuild-flink
```

### 数据库连接失败

```bash
# 检查 Oracle 容器状态
docker ps | grep oracle

# 检查网络连接
docker network inspect flink-network | grep oracle

# 重新连接网络
docker network connect flink-network oracle11g
docker-compose restart monitor-backend
```

### 前端无法访问后端

```bash
# 检查后端健康状态
curl http://localhost:5001/actuator/health

# 检查 nginx 配置
docker exec -it flink-monitor-frontend cat /etc/nginx/conf.d/default.conf

# 查看 nginx 日志
docker-compose logs -f monitor-frontend
```

## 维护命令

### 备份 Checkpoint 和 Savepoint

```bash
# 创建备份目录
mkdir -p backups/$(date +%Y%m%d)

# 备份数据
cp -r data/flink-checkpoints backups/$(date +%Y%m%d)/
cp -r data/flink-savepoints backups/$(date +%Y%m%d)/
```

### 清理旧日志

```bash
# 清理 Docker 日志
docker system prune -f

# 清理 Flink 日志（保留最近 7 天）
find data/flink-logs -name "*.log" -mtime +7 -delete
```

### 更新镜像

```bash
# 拉取最新基础镜像
docker pull eclipse-temurin:17-jre
docker pull nginx:alpine
docker pull node:18-alpine

# 重新构建
./start.sh --rebuild-flink
```

## 安全建议

1. ✅ 修改默认管理员密码
2. ✅ 配置 JWT 密钥（已在 .env 中）
3. ✅ 配置 AES 加密密钥（已在 .env 中）
4. ⚠️ 启用 HTTPS（生产环境）
5. ⚠️ 配置防火墙规则
6. ⚠️ 定期更新依赖和镜像
7. ⚠️ 启用访问日志审计
8. ⚠️ 使用密钥管理服务（如 AWS KMS）

## 相关文档

- [START-SCRIPT-FIX.md](./START-SCRIPT-FIX.md) - 启动脚本修复指南
- [DOCKER-NETWORK-FIX.md](./DOCKER-NETWORK-FIX.md) - Docker 网络问题修复
- [CHINA-MIRRORS.md](./CHINA-MIRRORS.md) - 国内镜像源配置
- [BUILD-GUIDE.md](./BUILD-GUIDE.md) - 构建指南
- [COMPLETE-STARTUP-GUIDE.md](./COMPLETE-STARTUP-GUIDE.md) - 完整启动指南
- [ARCHITECTURE.md](./ARCHITECTURE.md) - 系统架构文档

## 技术支持

如遇到问题，请：

1. 查看相关文档
2. 检查日志输出
3. 验证网络连接
4. 确认配置正确

## 总结

系统已成功启动！所有服务运行正常，可以开始使用 CDC 数据采集功能。

**主要成就：**
- ✅ 解决了 4 个关键启动问题
- ✅ 配置了国内镜像源加速
- ✅ 建立了完整的 Docker 网络
- ✅ 所有服务健康检查通过
- ✅ 前后端通信正常
- ✅ 数据库连接成功

**下一步：**
1. 登录前端界面
2. 配置数据源
3. 创建 CDC 任务
4. 监控实时数据流

祝使用愉快！🚀
