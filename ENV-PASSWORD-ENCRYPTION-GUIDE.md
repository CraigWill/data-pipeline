# 环境变量密码加密指南

## 概述

本指南说明如何在 `.env` 文件中使用加密密码，提高系统安全性。

## 功能特性

- 支持 `.env` 文件中的数据库密码加密存储
- 使用 AES 加密算法（与数据源密码加密相同）
- 自动检测密码格式（明文或密文）
- 向后兼容明文密码（开发环境）
- 提供命令行加密工具

## 使用方法

### 1. 设置 AES 加密密钥

首先在 `.env` 文件中设置 AES 加密密钥：

```bash
# 生成 32 字节的随机密钥
openssl rand -base64 32

# 或者使用固定长度的密钥（必须是 16、24 或 32 字节）
AES_ENCRYPTION_KEY=your-32-byte-aes-key-here-123456
```

**重要提示**:
- 密钥长度必须是 16、24 或 32 字节
- 生产环境必须使用强随机密钥
- 密钥丢失后无法解密已加密的密码

### 2. 加密数据库密码

使用提供的加密脚本：

```bash
# 基本用法
./encrypt-password.sh "your-plain-password"

# 示例
./encrypt-password.sh "mySecretPassword123"
```

输出示例：

```
==========================================
密码加密工具
==========================================

使用 Java 加密...
✓ 加密成功！

原始密码: mySecretPassword123
加密密码: Qb2VqJ0n4iE5xlXhUNKU1A==

请将以下内容复制到 .env 文件中:
DATABASE_PASSWORD=Qb2VqJ0n4iE5xlXhUNKU1A==
```

### 3. 更新 .env 文件

将加密后的密码复制到 `.env` 文件：

```bash
# 使用加密密码（推荐）
DATABASE_PASSWORD=Qb2VqJ0n4iE5xlXhUNKU1A==

# 或者使用明文密码（仅开发环境）
DATABASE_PASSWORD=mySecretPassword123
```

### 4. 重启服务

```bash
# 重启所有服务以应用新配置
docker-compose restart

# 或者只重启相关服务
docker-compose restart jobmanager taskmanager monitor-backend
```

## 工作原理

### 加密流程

1. 用户运行 `encrypt-password.sh` 脚本
2. 脚本从 `.env` 文件读取 `AES_ENCRYPTION_KEY`
3. 使用 Java AES 加密算法加密明文密码
4. 输出 Base64 编码的加密密码

### 解密流程

1. 应用启动时从环境变量读取 `DATABASE_PASSWORD`
2. 检测密码格式（Base64 编码 = 密文）
3. 尝试使用 `AES_ENCRYPTION_KEY` 解密
4. 解密成功：使用解密后的密码
5. 解密失败：使用原密码（兼容明文）

### 自动检测逻辑

系统会自动检测密码格式：

```java
// 伪代码
String password = System.getenv("DATABASE_PASSWORD");
try {
    // 尝试解密
    String decrypted = PasswordEncryptionUtil.decryptAES(password);
    // 使用解密后的密码
    return decrypted;
} catch (Exception e) {
    // 解密失败，使用原密码（明文）
    return password;
}
```

这种设计确保：
- 加密密码会被自动解密
- 明文密码可以直接使用
- 向后兼容现有配置

## 安全建议

### 开发环境

- 可以使用明文密码（方便调试）
- 使用默认的 AES 密钥
- 不要将 `.env` 文件提交到版本控制

### 生产环境

- **必须**使用加密密码
- **必须**使用强随机 AES 密钥
- **必须**定期轮换密钥和密码
- 使用密钥管理服务（如 AWS KMS、阿里云 KMS）
- 限制 `.env` 文件的访问权限（chmod 600）
- 定期审计密钥使用情况

### 密钥管理

1. **生成强密钥**
   ```bash
   # 使用 OpenSSL 生成随机密钥
   openssl rand -base64 32
   
   # 或者使用 /dev/urandom
   head -c 32 /dev/urandom | base64
   ```

2. **安全存储密钥**
   - 不要硬编码在代码中
   - 不要提交到版本控制
   - 使用环境变量或密钥管理服务
   - 定期备份密钥（加密存储）

3. **密钥轮换**
   ```bash
   # 1. 生成新密钥
   NEW_KEY=$(openssl rand -base64 32)
   
   # 2. 使用新密钥重新加密所有密码
   ./encrypt-password.sh "password1"
   ./encrypt-password.sh "password2"
   
   # 3. 更新 .env 文件
   # AES_ENCRYPTION_KEY=<new-key>
   # DATABASE_PASSWORD=<new-encrypted-password>
   
   # 4. 重启服务
   docker-compose restart
   ```

## 故障排查

### 问题 1: 加密脚本报错 "未找到 AES_ENCRYPTION_KEY"

**原因**: `.env` 文件中没有设置 `AES_ENCRYPTION_KEY`

**解决方案**:
```bash
# 在 .env 文件中添加
AES_ENCRYPTION_KEY=your-32-byte-aes-key-here-123456
```

### 问题 2: 加密失败 "Invalid AES key length"

**原因**: AES 密钥长度不正确

**解决方案**:
```bash
# 确保密钥长度是 16、24 或 32 字节
echo -n "your-key" | wc -c  # 检查长度

# 使用正确长度的密钥
AES_ENCRYPTION_KEY=your-32-byte-aes-key-here-123456  # 32 字节
```

### 问题 3: 数据库连接失败

**原因**: 密码解密失败或密钥不匹配

**解决方案**:
```bash
# 1. 检查 AES_ENCRYPTION_KEY 是否正确
grep AES_ENCRYPTION_KEY .env

# 2. 重新加密密码
./encrypt-password.sh "your-plain-password"

# 3. 更新 .env 文件
# DATABASE_PASSWORD=<new-encrypted-password>

# 4. 重启服务
docker-compose restart
```

### 问题 4: 加密脚本找不到 Java

**原因**: 系统未安装 Java

**解决方案**:
```bash
# macOS
brew install openjdk

# Ubuntu/Debian
apt-get install default-jdk

# CentOS/RHEL
yum install java-17-openjdk

# 验证安装
java -version
```

## 测试

### 测试加密功能

```bash
# 1. 加密测试密码
./encrypt-password.sh "testPassword123"

# 2. 复制加密密码到 .env
# DATABASE_PASSWORD=<encrypted-password>

# 3. 测试数据库连接
docker-compose up -d monitor-backend
docker-compose logs monitor-backend | grep -i "database\|connection"
```

### 测试解密功能

```bash
# 1. 使用加密密码启动服务
docker-compose up -d

# 2. 检查日志，确认连接成功
docker-compose logs monitor-backend | grep "Connected to database"

# 3. 测试 API
curl http://localhost:5001/api/datasources
```

## 示例配置

### 开发环境 (.env)

```bash
# 开发环境可以使用明文密码
DATABASE_HOST=localhost
DATABASE_PORT=1521
DATABASE_USERNAME=finance_user
DATABASE_PASSWORD=dev_password_123

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
DATABASE_PASSWORD=xK8vN2pQ7mL4tR9sW3nY...  # 加密密码

# 使用强随机密钥
AES_ENCRYPTION_KEY=<strong-random-key-from-kms>
JWT_SECRET=<strong-random-jwt-secret>
```

## 相关文档

- [SECURITY-DEPLOYMENT-GUIDE.md](SECURITY-DEPLOYMENT-GUIDE.md) - 安全部署指南
- [SECURITY-IMPLEMENTATION-GUIDE.md](SECURITY-IMPLEMENTATION-GUIDE.md) - 安全实现指南
- [.env.example](.env.example) - 环境变量配置示例

## 常见问题

### Q: 为什么需要加密环境变量中的密码？

A: 虽然 `.env` 文件不应提交到版本控制，但在生产环境中：
- 文件可能被意外泄露
- 系统管理员可能有访问权限
- 日志或备份可能包含环境变量
- 加密提供额外的安全层

### Q: 加密密码和明文密码可以混用吗？

A: 可以。系统会自动检测密码格式：
- 加密密码会被解密后使用
- 明文密码直接使用
- 但建议统一使用加密密码

### Q: 如何知道密码是否被正确解密？

A: 检查应用日志：
```bash
# 查看数据库连接日志
docker-compose logs monitor-backend | grep -i "database\|connection"

# 如果看到 "Connected to database" 说明密码正确
# 如果看到 "Authentication failed" 说明密码错误
```

### Q: 密钥丢失了怎么办？

A: 密钥丢失后无法解密已加密的密码，需要：
1. 生成新密钥
2. 使用明文密码重新加密
3. 更新所有配置文件
4. 重启所有服务

### Q: 可以使用其他加密算法吗？

A: 当前实现使用 AES 算法。如需使用其他算法：
1. 修改 `PasswordEncryptionUtil.java`
2. 修改 `encrypt-password.sh`
3. 确保加密和解密使用相同算法
4. 重新编译和部署

## 代码集成

### 在 Java 代码中使用环境变量密码

系统提供了 `EnvironmentPasswordUtil` 工具类，用于自动解密环境变量中的密码：

```java
import com.realtime.monitor.util.EnvironmentPasswordUtil;

// 获取并自动解密密码
String dbPassword = EnvironmentPasswordUtil.getPassword("DATABASE_PASSWORD");

// 带默认值
String dbPassword = EnvironmentPasswordUtil.getPassword("DATABASE_PASSWORD", "default-password");

// 检查密码是否可能是加密的
boolean isEncrypted = EnvironmentPasswordUtil.isPossiblyEncrypted(password);
```

### 工作原理

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

### 使用场景

1. **Docker 容器环境变量**
   ```yaml
   # docker-compose.yml
   services:
     monitor-backend:
       environment:
         - DATABASE_PASSWORD=${DATABASE_PASSWORD}
   ```

2. **应用配置**
   ```java
   // 从环境变量读取并自动解密
   String password = EnvironmentPasswordUtil.getPassword("DATABASE_PASSWORD");
   Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
   ```

3. **数据源配置**
   ```java
   // 数据源配置文件中的密码已加密
   DataSourceConfig config = dataSourceService.loadDataSource("ds-001");
   // config.getPassword() 返回解密后的明文密码
   ```

## 总结

环境变量密码加密功能提供了：
- ✓ 简单易用的加密工具
- ✓ 自动检测密码格式
- ✓ 向后兼容明文密码
- ✓ 与数据源密码加密统一
- ✓ 生产环境安全保障
- ✓ 环境变量密码自动解密

建议在生产环境中始终使用加密密码，并定期轮换密钥。
