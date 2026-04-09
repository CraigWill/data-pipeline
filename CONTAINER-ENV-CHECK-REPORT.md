# Docker 容器环境变量检查报告

## 检查时间
2026年3月30日

## 检查结果

### ✅ 所有容器环境变量配置正确

## 容器环境变量详情

### 1. monitor-backend

**状态**: ✅ 所有关键环境变量已正确设置

| 环境变量 | 状态 | 值（部分） | 长度 |
|---------|------|-----------|------|
| JWT_SECRET | ✅ | xGTMWGPrwsj9s1DtwQuQ... | 88 字符 |
| AES_ENCRYPTION_KEY | ✅ | E5TDKE7NvyphzdaW0SIU... | 44 字符 |
| DATABASE_PASSWORD | ✅ | Lek5PkYJ7Q... | 加密 |
| DATABASE_HOST | ✅ | host.docker.internal | - |
| DATABASE_PORT | ✅ | 1521 | - |
| DATABASE_USERNAME | ✅ | finance_user | - |
| DATABASE_SID | ✅ | helowin | - |

**说明**:
- JWT_SECRET 和 AES_ENCRYPTION_KEY 长度正确
- DATABASE_PASSWORD 使用加密格式
- 所有必需的环境变量都已设置

### 2. jobmanager

**状态**: ✅ 数据库和 Flink 配置正确

| 环境变量 | 值 |
|---------|-----|
| DATABASE_HOST | host.docker.internal |
| DATABASE_PORT | 1521 |
| DATABASE_USERNAME | finance_user |
| DATABASE_PASSWORD | Lek5PkYJ7Q... (加密) |
| DATABASE_SID | helowin |
| DATABASE_SCHEMA | FINANCE_USER |
| DATABASE_TABLES | TRANS_INFO |
| CHECKPOINT_DIR | file:///opt/flink/checkpoints |
| CHECKPOINT_INTERVAL | 300000 |
| HA_MODE | zookeeper |
| HA_ZOOKEEPER_QUORUM | zookeeper:2181 |

**说明**:
- 数据库连接配置完整
- Checkpoint 配置正确
- 高可用配置正确

### 3. jobmanager-standby

**状态**: ✅ 配置与主 JobManager 一致

配置与 jobmanager 相同，用于高可用备份。

### 4. taskmanager

**状态**: ✅ Flink 配置正确

| 环境变量 | 值 |
|---------|-----|
| FLINK_VERSION | 1.20.3 |
| FLINK_HOME | /opt/flink |
| TASK_MANAGER_HEAP_SIZE | 1024m |
| TASK_MANAGER_MEMORY_PROCESS_SIZE | 1728m |
| TASK_MANAGER_NUMBER_OF_TASK_SLOTS | 2 |
| TASK_MANAGER_RPC_PORT | 6122 |
| TASK_MANAGER_DATA_PORT | 6121 |

**说明**:
- TaskManager 内存配置正确
- 任务槽配置正确

### 5. monitor-frontend

**状态**: ✅ 前端配置正常

| 环境变量 | 值 |
|---------|-----|
| NGINX_VERSION | 1.29.5 |

**说明**:
- 前端使用 Nginx，不需要额外的环境变量

### 6. zookeeper

**状态**: ✅ ZooKeeper 配置正确

| 环境变量 | 值 |
|---------|-----|
| ZOOKEEPER_CLIENT_PORT | 2181 |
| ZOOKEEPER_TICK_TIME | 2000 |
| ZOOKEEPER_SYNC_LIMIT | 2 |

**说明**:
- ZooKeeper 配置正确，用于 Flink 高可用

## 环境变量来源

所有容器的环境变量都从以下来源加载：

1. **.env 文件**（项目根目录）
   - Docker Compose 自动加载
   - 包含所有关键配置

2. **docker-compose.yml**
   - 定义了环境变量映射
   - 使用 `${VAR_NAME}` 语法引用 .env 文件

3. **Dockerfile**
   - 定义了默认环境变量
   - 可被 docker-compose.yml 覆盖

## 环境变量传递流程

```
.env 文件
  ↓
docker-compose.yml (读取 .env)
  ↓
容器环境变量
  ↓
应用程序 (读取环境变量)
```

## 验证结果

### ✅ 验证通过

1. **JWT_SECRET**
   - 长度: 88 字符 ✅
   - 格式: Base64 ✅
   - 在 monitor-backend 中可用 ✅

2. **AES_ENCRYPTION_KEY**
   - 长度: 44 字符 ✅
   - 格式: Base64 ✅
   - 在 monitor-backend 中可用 ✅

3. **DATABASE_PASSWORD**
   - 格式: 加密（Base64）✅
   - 在 monitor-backend 中可用 ✅
   - 在 jobmanager 中可用 ✅

4. **数据库连接配置**
   - DATABASE_HOST ✅
   - DATABASE_PORT ✅
   - DATABASE_USERNAME ✅
   - DATABASE_SID ✅

## 常见操作

### 查看特定容器的环境变量

```bash
# monitor-backend
docker-compose exec monitor-backend env | grep -E "JWT_SECRET|AES_ENCRYPTION_KEY|DATABASE"

# jobmanager
docker-compose exec jobmanager env | grep -E "DATABASE|CHECKPOINT|FLINK"

# taskmanager
docker-compose exec taskmanager env | grep -E "FLINK|TASK_MANAGER"
```

### 更新环境变量

```bash
# 1. 修改 .env 文件
nano .env

# 2. 重启容器（重新加载环境变量）
docker-compose restart

# 3. 验证
./check-container-env.sh
```

### 完全重新加载

```bash
# 1. 停止所有容器
docker-compose down

# 2. 启动（重新读取 .env）
docker-compose up -d

# 3. 验证
./check-container-env.sh
```

## 故障排查

### 问题 1: 容器内环境变量未设置

**症状**: `docker-compose exec monitor-backend env | grep JWT_SECRET` 返回空

**原因**: .env 文件未被 Docker Compose 读取

**解决方案**:
```bash
# 1. 验证 .env 文件存在
ls -la .env

# 2. 验证 docker-compose.yml 配置
docker-compose config | grep JWT_SECRET

# 3. 重新启动
docker-compose down
docker-compose up -d

# 4. 验证
./check-container-env.sh
```

### 问题 2: 环境变量值不正确

**症状**: 环境变量存在但值不对

**原因**: .env 文件中的值不正确或被其他配置覆盖

**解决方案**:
```bash
# 1. 检查 .env 文件
cat .env | grep JWT_SECRET

# 2. 检查 docker-compose.yml 是否有硬编码值
grep -A 5 "environment:" docker-compose.yml

# 3. 重新生成密钥（如果需要）
./init-keys.sh

# 4. 重启容器
docker-compose restart
```

### 问题 3: 多行值被截断

**症状**: JWT_SECRET 长度不是 88

**原因**: 环境变量包含换行符

**解决方案**:
```bash
# 1. 检查 .env 文件格式
cat -A .env | grep JWT_SECRET

# 2. 确保值在同一行
# 使用文本编辑器移除换行符

# 3. 重启容器
docker-compose restart

# 4. 验证长度
docker-compose exec monitor-backend env | grep "^JWT_SECRET=" | wc -c
```

### 问题 4: 容器启动失败

**症状**: 容器状态为 Exited 或 Restarting

**解决方案**:
```bash
# 1. 查看容器日志
docker-compose logs monitor-backend | tail -50

# 2. 检查是否有密钥未设置的错误
docker-compose logs monitor-backend | grep -i "未设置\|not set\|required"

# 3. 验证环境变量
./check-container-env.sh

# 4. 如果环境变量正确但仍失败，检查其他错误
docker-compose logs monitor-backend
```

## 安全检查

### ✅ 安全措施已实施

1. **密钥使用环境变量**
   - 不在 Dockerfile 中硬编码
   - 不在 docker-compose.yml 中硬编码
   - 从 .env 文件加载

2. **.env 文件保护**
   - 文件权限: 600
   - 已添加到 .gitignore
   - 不会提交到版本控制

3. **密码加密**
   - DATABASE_PASSWORD 使用 AES 加密
   - 容器内自动解密

4. **密钥强度**
   - JWT_SECRET: 64 字节（88 字符 Base64）
   - AES_ENCRYPTION_KEY: 32 字节（44 字符 Base64）

### ⚠️ 注意事项

1. **不要在日志中打印密钥**
   ```bash
   # 不要运行
   docker-compose logs | grep JWT_SECRET
   ```

2. **不要导出容器配置**
   ```bash
   # 小心使用
   docker inspect <container> | grep -i secret
   ```

3. **定期轮换密钥**
   ```bash
   # 每 90 天或安全事件后
   ./init-keys.sh
   docker-compose restart
   ```

## 工具脚本

### check-container-env.sh

检查所有容器的环境变量配置。

**使用方法**:
```bash
./check-container-env.sh
```

**功能**:
- 列出所有运行中的服务
- 显示每个服务的关键环境变量
- 验证必需的环境变量
- 检查环境变量长度和格式

### 其他相关脚本

```bash
# 配置系统环境变量
./setup-env-vars.sh

# 验证系统环境变量
./verify-env.sh

# 生成新密钥
./init-keys.sh

# 加密密码
./encrypt-password.sh "password"
```

## 相关文档

- [SYSTEM-ENV-SETUP-GUIDE.md](SYSTEM-ENV-SETUP-GUIDE.md) - 系统环境变量配置指南
- [ENV-QUICK-REFERENCE.md](ENV-QUICK-REFERENCE.md) - 环境变量快速参考
- [KEY-MANAGEMENT-GUIDE.md](KEY-MANAGEMENT-GUIDE.md) - 密钥管理指南
- [REMOVE-DEFAULT-PASSWORDS.md](REMOVE-DEFAULT-PASSWORDS.md) - 移除默认密码总结

## 总结

所有 Docker 容器的环境变量配置正确：

✅ **monitor-backend**: JWT_SECRET, AES_ENCRYPTION_KEY, DATABASE_* 全部正确
✅ **jobmanager**: DATABASE_*, CHECKPOINT_*, HA_* 全部正确
✅ **jobmanager-standby**: 配置与主节点一致
✅ **taskmanager**: FLINK_*, TASK_MANAGER_* 全部正确
✅ **monitor-frontend**: Nginx 配置正确
✅ **zookeeper**: ZooKeeper 配置正确

**环境变量来源**: .env 文件 → docker-compose.yml → 容器

**验证工具**: `./check-container-env.sh`

**下一步**: 容器环境变量已正确配置，可以正常使用应用。
