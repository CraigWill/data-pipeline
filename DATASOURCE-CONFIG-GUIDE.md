# 数据源配置指南

## 🔍 问题说明

如果在前端创建数据源时遇到 "获取 Schema 列表失败" 错误，通常是因为数据库主机地址配置不正确。

### 错误示例

```
获取 Schema 列表失败: ds-1775807601933
java.sql.SQLRecoverableException: IO Error: The Network Adapter could not establish the connection
```

## ✅ 正确配置

### 在 Docker 环境中

当应用运行在 Docker 容器中时，**必须使用容器名**而不是 `localhost` 或 `host.docker.internal`。

#### 正确的配置

| 字段 | 值 | 说明 |
|------|-----|------|
| 数据源名称 | Oracle CDC | 任意名称 |
| 主机地址 | **oracle11g** | ✅ 使用容器名 |
| 端口 | 1521 | Oracle 默认端口 |
| 用户名 | finance_user | 数据库用户 |
| 密码 | (你的密码) | 数据库密码 |
| SID | helowin | 数据库实例名 |

#### 错误的配置

❌ **不要使用：**
- `localhost` - 在容器内指向容器自己
- `127.0.0.1` - 同上
- `host.docker.internal` - 虽然在 macOS 上可用，但不推荐
- 宿主机 IP - 可能会变化

## 🎯 配置步骤

### 1. 访问前端界面

```
http://localhost:8888
```

### 2. 登录系统

默认账号：
- 用户名：`admin`
- 密码：`admin123`

### 3. 创建数据源

点击 "数据源管理" → "新建数据源"

填写配置：
```
数据源名称: Oracle CDC
主机地址: oracle11g          ← 重要！使用容器名
端口: 1521
用户名: finance_user
密码: (从 .env 文件获取)
SID: helowin
描述: Oracle 11g CDC 数据源
```

### 4. 测试连接

点击 "测试连接" 按钮，应该显示：
```
✅ 连接成功
```

### 5. 保存数据源

点击 "保存" 按钮。

### 6. 获取 Schema 列表

保存后，系统会自动获取 Schema 列表，应该看到：
```
- FINANCE_USER
- (其他 Schema)
```

## 🔧 获取数据库密码

如果不知道数据库密码，可以从 `.env` 文件获取：

```bash
# 查看加密的密码
cat .env | grep DATABASE_PASSWORD

# 输出示例
DATABASE_PASSWORD=CO9AKFeKC6vYyJ5yd6bXSQ==
```

这是加密后的密码。如果需要明文密码，可以：

### 方法 1：从 Oracle 容器内查看

```bash
docker exec oracle11g bash -c "
  source /home/oracle/.bash_profile
  sqlplus / as sysdba <<EOF
  SELECT username FROM dba_users WHERE username = 'FINANCE_USER';
  EXIT;
EOF
"
```

### 方法 2：重置密码

```bash
docker exec oracle11g bash -c "
  source /home/oracle/.bash_profile
  sqlplus / as sysdba <<EOF
  ALTER USER finance_user IDENTIFIED BY newpassword;
  EXIT;
EOF
"
```

然后更新 `.env` 文件中的密码。

## 🌐 网络架构

### Docker 网络拓扑

```
┌─────────────────────────────────────────────────────────┐
│ flink-network (Docker Bridge Network)                  │
│                                                         │
│  ┌──────────────┐                                      │
│  │ Monitor      │                                      │
│  │ Backend      │                                      │
│  │ (容器)       │                                      │
│  └──────┬───────┘                                      │
│         │                                              │
│         │ JDBC: jdbc:oracle:thin:@oracle11g:1521:helowin
│         │                                              │
│         ↓                                              │
│  ┌──────────────┐                                      │
│  │ Oracle 11g   │                                      │
│  │ (oracle11g)  │                                      │
│  │ 容器名       │                                      │
│  └──────────────┘                                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 关键点

1. **容器间通信**：使用容器名（`oracle11g`）
2. **宿主机访问**：使用 `localhost:1521`
3. **容器内访问宿主机**：使用 `host.docker.internal`（不推荐）

## 🐛 故障排查

### 问题 1：连接超时

**症状：**
```
Connection timeout
```

**原因：** Oracle 容器未运行或不在同一网络

**解决：**
```bash
# 检查 Oracle 容器
docker ps | grep oracle

# 检查网络
docker network inspect flink-network | grep oracle11g

# 如果不在网络中，连接到网络
docker network connect flink-network oracle11g
```

### 问题 2：Network Adapter 错误

**症状：**
```
The Network Adapter could not establish the connection
```

**原因：** 主机地址配置错误

**解决：**
- 确保使用 `oracle11g` 作为主机地址
- 不要使用 `localhost` 或 `127.0.0.1`

### 问题 3：Invalid username/password

**症状：**
```
ORA-01017: invalid username/password
```

**原因：** 用户名或密码错误

**解决：**
```bash
# 测试连接
docker exec oracle11g bash -c "
  source /home/oracle/.bash_profile
  sqlplus finance_user/yourpassword@helowin
"
```

### 问题 4：Listener 错误

**症状：**
```
ORA-12541: TNS:no listener
```

**原因：** Oracle 监听器未启动

**解决：**
```bash
# 重启 Oracle 容器
docker restart oracle11g

# 等待 30 秒让服务启动
sleep 30

# 检查监听器
docker exec oracle11g bash -c "
  source /home/oracle/.bash_profile
  lsnrctl status
"
```

## 📝 配置模板

### 开发环境

```json
{
  "name": "Oracle CDC - Dev",
  "host": "oracle11g",
  "port": 1521,
  "username": "finance_user",
  "password": "your_password",
  "sid": "helowin",
  "description": "开发环境 Oracle 数据源"
}
```

### 生产环境

```json
{
  "name": "Oracle CDC - Prod",
  "host": "prod-oracle-host",
  "port": 1521,
  "username": "finance_user",
  "password": "encrypted_password",
  "sid": "PROD",
  "description": "生产环境 Oracle 数据源"
}
```

## ✅ 验证清单

配置数据源前，确认以下项目：

- [ ] Oracle 容器正在运行：`docker ps | grep oracle`
- [ ] Oracle 在 flink-network：`docker network inspect flink-network | grep oracle`
- [ ] 可以从容器连接：`docker exec flink-monitor-backend nc -zv oracle11g 1521`
- [ ] 数据库用户存在：`SELECT username FROM dba_users WHERE username = 'FINANCE_USER'`
- [ ] 数据库权限正确：运行 `sql/04-check-cdc-status.sql`

## 🆘 获取帮助

如果问题仍未解决：

1. **查看后端日志：**
   ```bash
   docker-compose logs monitor-backend --tail 100
   ```

2. **测试数据库连接：**
   ```bash
   docker exec flink-monitor-backend sh -c "
     nc -zv oracle11g 1521
   "
   ```

3. **检查网络配置：**
   ```bash
   docker network inspect flink-network
   ```

4. **查看详细文档：**
   - [NETWORK-SETUP.md](NETWORK-SETUP.md) - 网络配置
   - [QUICK-START.md](QUICK-START.md) - 快速启动
   - [SQL-EXECUTION-SUMMARY.md](SQL-EXECUTION-SUMMARY.md) - 数据库配置

---

**最后更新：** 2026-04-10  
**版本：** 1.0
