# 快速启动指南

## 📋 前提条件

- ✅ Oracle 11g 容器正在运行
- ✅ Docker 和 Docker Compose 已安装
- ✅ Java 17+ 已安装（用于构建）
- ✅ Oracle 容器已连接到 flink-network

### 配置 Docker 网络

如果这是首次运行，需要将 Oracle 容器连接到 flink-network：

```bash
# 将 Oracle 容器连接到 flink-network
docker network connect flink-network oracle11g

# 验证连接
docker network inspect flink-network | grep oracle11g
```

确保 `.env` 文件中的配置正确：

```bash
# 应该使用容器名
DATABASE_HOST=oracle11g

# 不要使用
# DATABASE_HOST=host.docker.internal  # ❌
# DATABASE_HOST=localhost              # ❌
```

## 🚀 一键配置 Oracle CDC

```bash
./setup-oracle-cdc.sh
```

这个脚本会自动执行：
1. 启用归档日志和补充日志
2. 创建 LOG_MINING_FLUSH 表
3. 创建 CDC 元数据表（cdc_datasources, cdc_tasks, runtime_jobs）
4. 验证所有配置

## 📊 架构说明

### 数据库架构（简化版）

所有表都在 **FINANCE_USER** schema 下：

```
FINANCE_USER
├── 业务表
│   ├── TRANS_INFO          # 交易表
│   ├── ACCOUNT_INFO        # 账户表
│   └── CLM_HISTORY         # 历史表
│
├── CDC 元数据表
│   ├── CDC_DATASOURCES     # 数据源配置
│   ├── CDC_TASKS           # 任务配置
│   └── RUNTIME_JOBS        # 运行时作业
│
└── CDC 内部表
    └── LOG_MINING_FLUSH    # LogMiner flush 表

表空间: TRANS_TBS (统一)
```

### 为什么简化？

**之前的架构：**
- FINANCE_USER (业务表) + FLINK_USER (CDC 表)
- TRANS_TBS + FLINK_TBS
- 需要跨用户授权

**现在的架构：**
- 只有 FINANCE_USER
- 只有 TRANS_TBS
- 无需跨用户授权
- 管理更简单

## 🔧 手动配置（可选）

如果需要手动执行每个步骤：

```bash
# 步骤 1: 启用 Oracle CDC
./execute-sql-sysdba.sh sql/01-enable-oracle-cdc.sql

# 步骤 2: 创建 CDC 元数据
./execute-sql-sysdba.sh sql/02-setup-cdc-metadata.sql

# 步骤 3: 创建业务元数据表
./execute-sql.sh sql/03-setup-metadata-tables.sql system helowin helowin

# 步骤 4: 验证配置
./execute-sql.sh sql/04-check-cdc-status.sql system helowin helowin
```

## 📦 构建和启动服务

### 1. 构建项目

```bash
# 快速构建（跳过测试）
./quick-build.sh

# 或完整构建
mvn clean package -DskipTests
```

### 2. 构建 Docker 镜像

```bash
./build-flink-images-cn.sh
```

### 3. 启动服务

```bash
docker-compose up -d
```

### 4. 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务
docker-compose logs -f flink-jobmanager
docker-compose logs -f monitor-backend
docker-compose logs -f monitor-frontend
```

## 🌐 访问服务

| 服务 | URL | 说明 |
|------|-----|------|
| 前端界面 | http://localhost:3000 | Vue.js 管理界面 |
| 监控后端 | http://localhost:8080 | Spring Boot API |
| Flink Web UI | http://localhost:8081 | Flink 管理界面 |

## ✅ 验证配置

### 检查数据库配置

```bash
./execute-sql.sh sql/04-check-cdc-status.sql system helowin helowin
```

应该看到：
- ✅ 归档日志模式: ARCHIVELOG
- ✅ 补充日志: MIN=YES, ALL=YES
- ✅ LOG_MINING_FLUSH 在 FINANCE_USER
- ✅ 元数据表已创建

### 检查 Flink 集群

```bash
curl http://localhost:8081/overview
```

应该返回 Flink 集群状态。

### 检查监控后端

```bash
curl http://localhost:8080/api/health
```

应该返回健康状态。

## 🔍 故障排查

### 网络连接问题

如果遇到 `Network Adapter could not establish the connection` 错误：

```bash
# 1. 检查 Oracle 容器是否在 flink-network
docker network inspect flink-network | grep oracle11g

# 2. 如果不在，连接到网络
docker network connect flink-network oracle11g

# 3. 重启服务
docker-compose restart monitor-backend
```

详细网络配置说明请参考：[NETWORK-SETUP.md](NETWORK-SETUP.md)

### Oracle 容器未运行

```bash
docker start oracle11g
docker logs -f oracle11g
```

### 数据库连接失败

检查 `.env` 文件中的数据库配置：
```bash
cat .env | grep DATABASE
```

### Flink 作业失败

查看 Flink JobManager 日志：
```bash
docker-compose logs flink-jobmanager
```

### 端口冲突

检查端口占用：
```bash
lsof -i :3000  # 前端
lsof -i :8080  # 后端
lsof -i :8081  # Flink
lsof -i :1521  # Oracle
```

## 📚 详细文档

- [SQL-MIGRATION-TO-FINANCE-USER.md](SQL-MIGRATION-TO-FINANCE-USER.md) - 架构迁移说明
- [SQL-SCRIPTS-CONSOLIDATION.md](SQL-SCRIPTS-CONSOLIDATION.md) - SQL 脚本整合
- [sql/README-SETUP-GUIDE.md](sql/README-SETUP-GUIDE.md) - 详细配置指南
- [ARCHITECTURE.md](ARCHITECTURE.md) - 系统架构文档
- [BUILD-GUIDE.md](BUILD-GUIDE.md) - 构建指南

## 🆘 获取帮助

如果遇到问题：

1. 查看日志：`docker-compose logs -f`
2. 检查配置：`./execute-sql.sh sql/04-check-cdc-status.sql system helowin helowin`
3. 查看故障排查文档：`sql/README-oracle-cdc-troubleshooting.md`

---

**最后更新：** 2026-04-10  
**版本：** 2.0 (简化架构)
