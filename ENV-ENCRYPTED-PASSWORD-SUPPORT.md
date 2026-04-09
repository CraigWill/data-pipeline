# .env 文件加密密码支持

## 概述

系统现在支持在 `.env` 文件中使用加密的数据库密码。密码会在使用时自动解密，提高了配置文件的安全性。

## 实现时间

2026年3月26日

## 功能特性

### 1. 自动检测和解密

系统会自动检测环境变量中的密码格式：
- **加密密码**（Base64 格式）：自动解密后使用
- **明文密码**：直接使用
- **向后兼容**：解密失败时回退到使用原密码

### 2. 支持的环境变量

- `DATABASE_PASSWORD` - 数据库密码
- 其他需要加密的敏感信息

### 3. 工具类

#### EnvironmentPasswordUtil
新增的环境变量密码工具类，提供以下功能：
- `getPassword(envVarName)` - 获取并自动解密密码
- `getPassword(envVarName, defaultValue)` - 获取密码（带默认值）
- `isPossiblyEncrypted(password)` - 检查密码是否可能是加密的

## 使用方法

### 1. 加密密码

使用提供的加密脚本：

```bash
./encrypt-password.sh "your-database-password"
```

输出示例：
```
✓ 加密成功！

原始密码: your-database-password
加密密码: V+uUYS1hqMh0uvC7JwxwIA==

请将以下内容复制到 .env 文件中:
DATABASE_PASSWORD=V+uUYS1hqMh0uvC7JwxwIA==
```

### 2. 更新 .env 文件

将加密后的密码复制到 `.env` 文件：

```bash
# 使用加密密码（推荐）
DATABASE_PASSWORD=V+uUYS1hqMh0uvC7JwxwIA==

# 或者使用明文密码（仅开发环境）
DATABASE_PASSWORD=plain-text-password
```

### 3. 重启服务

```bash
docker-compose restart
```

## 工作原理

### 密码处理流程

```
.env 文件
    ↓
Docker 容器环境变量
    ↓
Java 应用读取环境变量
    ↓
EnvironmentPasswordUtil.getPassword()
    ↓
检测密码格式
    ├─ Base64 格式 → 尝试 AES 解密
    │   ├─ 成功 → 返回明文密码
    │   └─ 失败 → 返回原密码（兼容明文）
    └─ 其他格式 → 直接返回（明文）
```

### 代码实现

```java
// 从环境变量获取密码（自动解密）
String password = EnvironmentPasswordUtil.getPassword("DATABASE_PASSWORD");

// 带默认值
String password = EnvironmentPasswordUtil.getPassword("DATABASE_PASSWORD", "default-password");
```

### 自动检测逻辑

```java
public static String getPassword(String envVarName, String defaultValue) {
    String password = System.getenv(envVarName);
    
    if (password == null || password.isEmpty()) {
        return defaultValue;
    }
    
    // 尝试解密
    try {
        return PasswordEncryptionUtil.decryptAES(password);
    } catch (Exception e) {
        // 解密失败，使用原密码（可能是明文）
        return password;
    }
}
```

## 应用场景

### 场景 1: Docker Compose 环境变量

在 `docker-compose.yml` 中：

```yaml
services:
  monitor-backend:
    environment:
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}  # 从 .env 读取
      - AES_ENCRYPTION_KEY=${AES_ENCRYPTION_KEY}
```

在 `.env` 文件中：

```bash
# 加密密码
DATABASE_PASSWORD=V+uUYS1hqMh0uvC7JwxwIA==
AES_ENCRYPTION_KEY=your-32-byte-aes-key-here-123456
```

### 场景 2: 数据源配置

数据源配置文件中的密码已加密：

```json
{
  "id": "ds-001",
  "name": "Production DB",
  "host": "db.example.com",
  "port": 1521,
  "username": "finance_user",
  "password": "xK8vN2pQ7mL4tR9sW3nY..."  // 加密密码
}
```

加载时自动解密：

```java
DataSourceConfig config = dataSourceService.loadDataSource("ds-001");
// config.getPassword() 返回明文密码
```

### 场景 3: 环境变量直接使用

在应用代码中：

```java
// 自动解密环境变量中的密码
String dbPassword = EnvironmentPasswordUtil.getPassword("DATABASE_PASSWORD");

// 使用密码连接数据库
Connection conn = DriverManager.getConnection(jdbcUrl, username, dbPassword);
```

## 安全特性

### 1. 多层加密保护

- **配置文件加密**: 数据源配置文件中的密码使用 AES 加密
- **环境变量加密**: `.env` 文件中的密码也可以使用 AES 加密
- **传输加密**: 建议使用 HTTPS 传输

### 2. 密钥管理

- AES 密钥从环境变量读取 (`AES_ENCRYPTION_KEY`)
- 生产环境必须使用强随机密钥
- 建议使用密钥管理服务（AWS KMS、阿里云 KMS）

### 3. 向后兼容

- 支持明文密码（开发环境）
- 支持加密密码（生产环境）
- 自动检测和处理

## 配置示例

### 开发环境 (.env)

```bash
# 开发环境可以使用明文密码
DATABASE_HOST=localhost
DATABASE_PORT=1521
DATABASE_USERNAME=finance_user
DATABASE_PASSWORD=dev_password_123  # 明文

# 使用默认密钥
AES_ENCRYPTION_KEY=your-32-byte-aes-key-here-123456
JWT_SECRET=dev-jwt-secret-key
```

### 生产环境 (.env)

```bash
# 生产环境必须使用加密密码
DATABASE_HOST=prod-db.example.com
DATABASE_PORT=1521
DATABASE_USERNAME=finance_user
DATABASE_PASSWORD=V+uUYS1hqMh0uvC7JwxwIA==  # 加密

# 使用强随机密钥
AES_ENCRYPTION_KEY=<strong-random-key-from-kms>
JWT_SECRET=<strong-random-jwt-secret>
```

## 测试验证

### 1. 测试加密功能

```bash
# 加密密码
$ ./encrypt-password.sh "testPassword123"
✓ 加密成功！
加密密码: Qb2VqJ0n4iE5xlXhUNKU1A==
```

### 2. 测试解密功能

```bash
# 更新 .env 文件
DATABASE_PASSWORD=Qb2VqJ0n4iE5xlXhUNKU1A==

# 重启服务
docker-compose restart monitor-backend

# 查看日志，确认密码解密成功
docker-compose logs monitor-backend | grep -i "password\|decrypt"
```

### 3. 测试数据库连接

```bash
# 测试连接应该成功
curl -X POST http://localhost:5001/api/datasources/test \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "host": "localhost",
    "port": 1521,
    "sid": "helowin",
    "username": "finance_user",
    "password": "testPassword123"
  }'
```

## 故障排查

### 问题 1: 密码解密失败

**症状**: 数据库连接失败，日志显示 "密码解密失败"

**原因**: 
- AES_ENCRYPTION_KEY 不正确
- 密码不是用当前密钥加密的

**解决方案**:
```bash
# 1. 确认 AES_ENCRYPTION_KEY 正确
grep AES_ENCRYPTION_KEY .env

# 2. 重新加密密码
./encrypt-password.sh "correct-password"

# 3. 更新 .env 文件
DATABASE_PASSWORD=<new-encrypted-password>

# 4. 重启服务
docker-compose restart
```

### 问题 2: 环境变量未生效

**症状**: 应用仍然使用旧密码

**原因**: 
- 环境变量未重新加载
- Docker 容器未重启

**解决方案**:
```bash
# 1. 停止所有容器
docker-compose down

# 2. 重新启动
docker-compose up -d

# 3. 验证环境变量
docker-compose exec monitor-backend env | grep DATABASE_PASSWORD
```

### 问题 3: 密码包含特殊字符

**症状**: 加密后的密码无法正确解密

**原因**: 
- 密码包含 shell 特殊字符
- 未正确引用

**解决方案**:
```bash
# 使用单引号或双引号包裹密码
./encrypt-password.sh 'password!@#$%'
./encrypt-password.sh "password!@#$%"
```

## 最佳实践

### 1. 开发环境

- 可以使用明文密码（方便调试）
- 使用默认 AES 密钥
- 不要将 `.env` 文件提交到版本控制

### 2. 测试环境

- 使用加密密码
- 使用独立的 AES 密钥
- 定期轮换密码

### 3. 生产环境

- **必须**使用加密密码
- **必须**使用强随机 AES 密钥
- 使用密钥管理服务（KMS）
- 限制 `.env` 文件访问权限（chmod 600）
- 定期轮换密钥和密码（每 90 天）
- 审计密钥使用情况

### 4. 密钥管理

```bash
# 生成强随机密钥
openssl rand -base64 32

# 限制文件权限
chmod 600 .env

# 定期备份密钥（加密存储）
gpg -c .env.backup
```

## 相关文件

### 新增文件
- `monitor-backend/src/main/java/com/realtime/monitor/util/EnvironmentPasswordUtil.java` - 环境变量密码工具类

### 修改文件
- `.env` - 添加加密密码支持说明
- `.env.example` - 添加加密密码使用示例

### 相关文档
- [ENV-PASSWORD-ENCRYPTION-GUIDE.md](ENV-PASSWORD-ENCRYPTION-GUIDE.md) - 密码加密完整指南
- [SECURITY-DEPLOYMENT-GUIDE.md](SECURITY-DEPLOYMENT-GUIDE.md) - 安全部署指南
- [QUICK-REFERENCE-PASSWORD-ENCRYPTION.md](QUICK-REFERENCE-PASSWORD-ENCRYPTION.md) - 快速参考

## 技术细节

### AES 加密算法

- **算法**: AES (Advanced Encryption Standard)
- **模式**: ECB (Electronic Codebook)
- **密钥长度**: 256 位 (32 字节)
- **编码**: Base64

### 密码格式检测

```java
public static boolean isPossiblyEncrypted(String password) {
    // Base64 字符集：A-Z, a-z, 0-9, +, /, =
    // 长度必须是 4 的倍数
    return password.matches("^[A-Za-z0-9+/]+=*$") && password.length() % 4 == 0;
}
```

### 性能考虑

- 密码解密只在应用启动时执行一次
- 解密后的明文密码缓存在内存中
- 对性能影响可忽略不计

## 后续改进

### 1. 支持多种加密算法

- AES-GCM（更安全）
- ChaCha20-Poly1305
- RSA 非对称加密

### 2. 密钥轮换自动化

- 自动生成新密钥
- 自动重新加密所有密码
- 自动更新配置文件

### 3. 密钥管理服务集成

- AWS KMS
- 阿里云 KMS
- HashiCorp Vault
- Azure Key Vault

### 4. 审计日志

- 记录密钥使用情况
- 记录解密操作
- 异常访问告警

## 总结

`.env` 文件加密密码支持已完成实现，主要成果：

✅ 创建了 `EnvironmentPasswordUtil` 工具类
✅ 支持自动检测和解密加密密码
✅ 向后兼容明文密码
✅ 提供了完整的使用文档和示例
✅ 实现了安全的密码管理机制

系统现在支持：
- 明文密码（开发环境）
- 加密密码（生产环境）
- 自动检测和解密
- 平滑迁移和部署

建议在生产环境中：
- 使用加密密码
- 使用强随机 AES 密钥
- 定期轮换密钥
- 使用密钥管理服务
- 限制文件访问权限

功能已就绪，可以投入使用！
