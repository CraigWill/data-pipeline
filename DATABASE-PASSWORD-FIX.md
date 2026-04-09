# 数据库密码解密失败问题修复

## 问题描述

monitor-backend 启动时出现数据库连接错误：
```
java.sql.SQLException: ORA-01005: null password given; logon denied
```

## 根本原因

系统环境变量 `DATABASE_PASSWORD` 中存储的是旧的加密密码（使用不同的 AES 密钥加密），与当前的 AES_ENCRYPTION_KEY 不匹配，导致解密失败。

### 问题详情

1. `.env` 文件中的密码：`CO9AKFeKC6vYyJ5yd6bXSQ==`（正确，使用当前密钥加密）
2. `~/.zshrc` 中的密码：`Lek5PkYJ7QflHC1spWlmMw==`（错误，使用旧密钥加密）
3. Docker Compose 优先使用系统环境变量，而不是 `.env` 文件
4. 解密失败时，`EnvironmentPasswordUtil` 返回加密值本身（而不是明文密码）
5. 使用加密值连接数据库导致 "null password" 错误

## 解决方案

### 1. 更新系统环境变量

```bash
# 编辑 ~/.zshrc
sed -i.bak 's/export DATABASE_PASSWORD="Lek5PkYJ7QflHC1spWlmMw=="/export DATABASE_PASSWORD="CO9AKFeKC6vYyJ5yd6bXSQ=="/' ~/.zshrc

# 重新加载配置
source ~/.zshrc
```

### 2. 重启容器

```bash
# 必须使用 down + up 来重新加载环境变量
docker-compose down
docker-compose up -d
```

注意：`docker-compose restart` 不会重新加载环境变量！

## 验证

### 1. 检查容器环境变量

```bash
docker exec flink-monitor-backend env | grep DATABASE_PASSWORD
# 应该输出: DATABASE_PASSWORD=CO9AKFeKC6vYyJ5yd6bXSQ==
```

### 2. 检查日志

```bash
docker logs flink-monitor-backend 2>&1 | grep "解密"
# 应该看到: 环境变量 DATABASE_PASSWORD 已成功解密
```

### 3. 检查健康状态

```bash
curl http://localhost:5001/actuator/health
# 应该看到: "db":{"status":"UP"}
```

## 经验教训

1. **环境变量优先级**：Docker Compose 按以下顺序读取环境变量：
   - Shell 环境变量（最高优先级）
   - `.env` 文件
   - `docker-compose.yml` 中的默认值

2. **密码加密一致性**：所有加密密码必须使用相同的 AES 密钥加密

3. **重启方式**：修改环境变量后必须使用 `docker-compose down && docker-compose up -d`

4. **错误处理**：解密失败时应该抛出异常，而不是返回加密值

## 相关文件

- `.env` - Docker Compose 环境变量配置
- `~/.zshrc` - Shell 环境变量配置
- `monitor-backend/src/main/java/com/realtime/monitor/util/EnvironmentPasswordUtil.java` - 密码解密工具
- `monitor-backend/src/main/java/com/realtime/monitor/util/PasswordEncryptionUtil.java` - AES 加密/解密实现

## 状态

✅ 已修复 - 数据库连接正常，应用健康
