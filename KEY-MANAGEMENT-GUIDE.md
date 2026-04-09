# 密钥管理指南

## 概述

本系统采用严格的密钥管理策略：
- **不在代码中存储任何默认密钥**
- **不在配置文件中存储密钥明文**
- **所有密钥必须通过环境变量设置**
- **提供自动化密钥生成工具**

## 密钥类型

### 1. JWT_SECRET
- **用途**: JWT Token 签名和验证
- **长度**: 64 字节（Base64 编码）
- **生成**: `openssl rand -base64 64`

### 2. AES_ENCRYPTION_KEY
- **用途**: 数据源密码和环境变量密码加密/解密
- **长度**: 32 字节（Base64 编码）
- **生成**: `openssl rand -base64 32`

## 快速开始

### 方法 1: 使用自动化脚本（推荐）

```bash
# 1. 复制示例配置
cp .env.example .env

# 2. 运行密钥初始化脚本
./init-keys.sh

# 3. 重启服务
docker-compose restart
```

### 方法 2: 手动生成

```bash
# 1. 生成密钥
echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')" >> .env
echo "AES_ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -d '\n')" >> .env

# 2. 限制文件权限
chmod 600 .env

# 3. 重启服务
docker-compose restart
```

## 密钥存储位置

### 唯一存储位置: .env 文件

```bash
# .env 文件（项目根目录）
JWT_SECRET=<生成的强随机密钥>
AES_ENCRYPTION_KEY=<生成的强随机密钥>
```

### 代码中的处理

```java
// PasswordEncryptionUtil.java
// 从环境变量读取，如果未设置则抛出异常
private static final String AES_KEY = getRequiredEnvVar("AES_ENCRYPTION_KEY");

private static String getRequiredEnvVar(String name) {
    String value = System.getenv(name);
    if (value == null || value.isEmpty()) {
        throw new IllegalStateException(
            "环境变量 " + name + " 未设置！请在 .env 文件中设置此变量。"
        );
    }
    return value;
}
```

### 配置文件中的处理

```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET}  # 必须通过环境变量设置，无默认值
  
encryption:
  aes-key: ${AES_ENCRYPTION_KEY}  # 必须通过环境变量设置，无默认值
```

## 安全特性

### 1. 强制密钥设置

如果环境变量未设置，应用启动时会抛出异常：

```
IllegalStateException: 环境变量 AES_ENCRYPTION_KEY 未设置！
请在 .env 文件中设置此变量。
生成密钥: openssl rand -base64 32
```

### 2. 无默认密钥

- 代码中不包含任何默认密钥
- 配置文件中不包含密钥明文
- 示例文件中只有占位符

### 3. 文件权限控制

```bash
# .env 文件权限应设置为 600（仅所有者可读写）
chmod 600 .env

# 验证权限
ls -l .env
# 应显示: -rw------- 1 user group ... .env
```

### 4. 版本控制排除

```bash
# .gitignore 已包含
.env
.env.backup.*
```

## 密钥轮换

### 何时需要轮换密钥

1. **定期轮换**: 每 90 天
2. **安全事件**: 密钥泄露或怀疑泄露
3. **人员变动**: 知道密钥的人员离职
4. **环境迁移**: 从开发环境到生产环境

### 轮换步骤

```bash
# 1. 备份当前密钥
cp .env .env.backup.$(date +%Y%m%d)

# 2. 生成新密钥
./init-keys.sh

# 3. 重新加密所有密码
# 注意: 旧密钥加密的密码需要用新密钥重新加密

# 4. 重启所有服务
docker-compose restart

# 5. 验证服务正常
docker-compose logs monitor-backend | grep -i "started\|error"
```

### 密钥轮换影响

⚠️ **重要**: 轮换 AES_ENCRYPTION_KEY 会导致：
- 所有已加密的数据源密码失效
- 需要重新创建或更新所有数据源
- 环境变量中的加密密码需要重新加密

## 密钥备份

### 备份方法

```bash
# 1. 加密备份
gpg -c .env
# 输出: .env.gpg

# 2. 存储到安全位置
# - 密钥管理服务（AWS KMS、阿里云 KMS）
# - 加密的云存储
# - 离线安全存储

# 3. 恢复备份
gpg -d .env.gpg > .env
chmod 600 .env
```

### 备份策略

1. **定期备份**: 每次更改密钥后立即备份
2. **多地备份**: 至少 2 个不同位置
3. **加密存储**: 备份文件必须加密
4. **访问控制**: 限制备份文件访问权限
5. **测试恢复**: 定期测试备份恢复流程

## 故障排查

### 问题 1: 应用启动失败 - 密钥未设置

**错误信息**:
```
IllegalStateException: 环境变量 AES_ENCRYPTION_KEY 未设置！
```

**解决方案**:
```bash
# 1. 检查 .env 文件
cat .env | grep -E "JWT_SECRET|AES_ENCRYPTION_KEY"

# 2. 如果密钥为空或占位符，运行初始化脚本
./init-keys.sh

# 3. 重启服务
docker-compose restart
```

### 问题 2: 密码解密失败

**错误信息**:
```
RuntimeException: AES 解密失败
```

**原因**: AES_ENCRYPTION_KEY 已更改，但密码是用旧密钥加密的

**解决方案**:
```bash
# 1. 恢复旧密钥（如果有备份）
cp .env.backup.YYYYMMDD .env

# 或者

# 2. 重新加密所有密码
./encrypt-password.sh "plain-password"
# 更新数据源配置
```

### 问题 3: Docker 容器无法读取环境变量

**原因**: .env 文件权限问题或 Docker Compose 未正确加载

**解决方案**:
```bash
# 1. 检查文件权限
ls -l .env

# 2. 验证 Docker Compose 配置
docker-compose config | grep -A 2 "JWT_SECRET\|AES_ENCRYPTION_KEY"

# 3. 重新启动（强制重新加载环境变量）
docker-compose down
docker-compose up -d

# 4. 验证容器内环境变量
docker-compose exec monitor-backend env | grep -E "JWT_SECRET|AES_ENCRYPTION_KEY"
```

## 生产环境部署

### 部署前检查清单

- [ ] 已生成强随机密钥（不使用示例密钥）
- [ ] .env 文件权限设置为 600
- [ ] .env 文件未提交到版本控制
- [ ] 密钥已安全备份
- [ ] 已测试应用启动和密码加密/解密
- [ ] 已配置密钥轮换计划
- [ ] 已限制密钥访问权限
- [ ] 已启用审计日志

### 推荐的密钥管理服务

#### AWS Secrets Manager
```bash
# 存储密钥
aws secretsmanager create-secret \
  --name prod/jwt-secret \
  --secret-string "$(openssl rand -base64 64)"

# 读取密钥
JWT_SECRET=$(aws secretsmanager get-secret-value \
  --secret-id prod/jwt-secret \
  --query SecretString \
  --output text)
```

#### 阿里云 KMS
```bash
# 使用阿里云 KMS 加密密钥
aliyun kms Encrypt \
  --KeyId <your-key-id> \
  --Plaintext "$(openssl rand -base64 64)"
```

#### HashiCorp Vault
```bash
# 存储密钥
vault kv put secret/prod/keys \
  jwt_secret="$(openssl rand -base64 64)" \
  aes_key="$(openssl rand -base64 32)"

# 读取密钥
vault kv get -field=jwt_secret secret/prod/keys
```

## 最佳实践

### ✅ 应该做的

1. **使用强随机密钥**
   - 使用 `openssl rand` 生成
   - 足够的长度（JWT: 64字节, AES: 32字节）

2. **限制访问权限**
   - .env 文件权限 600
   - 只有必要的人员知道密钥

3. **定期轮换**
   - 每 90 天轮换一次
   - 安全事件后立即轮换

4. **安全备份**
   - 加密存储备份
   - 多地备份
   - 定期测试恢复

5. **使用密钥管理服务**（生产环境）
   - AWS Secrets Manager
   - 阿里云 KMS
   - HashiCorp Vault

6. **审计和监控**
   - 记录密钥访问
   - 监控异常使用
   - 定期审查

### ❌ 不应该做的

1. ❌ 在代码中硬编码密钥
2. ❌ 在配置文件中存储密钥明文
3. ❌ 将 .env 文件提交到版本控制
4. ❌ 在日志中打印密钥
5. ❌ 在多个环境共享密钥
6. ❌ 使用弱密钥或默认密钥
7. ❌ 通过不安全的渠道传输密钥
8. ❌ 忽略密钥轮换
9. ❌ 不备份密钥
10. ❌ 给予过多人员密钥访问权限

## 相关文档

- [SECURITY-DEPLOYMENT-GUIDE.md](SECURITY-DEPLOYMENT-GUIDE.md) - 安全部署指南
- [ENV-PASSWORD-ENCRYPTION-GUIDE.md](ENV-PASSWORD-ENCRYPTION-GUIDE.md) - 密码加密指南
- [PASSWORD-ENCRYPTION-FINAL-SUMMARY.md](PASSWORD-ENCRYPTION-FINAL-SUMMARY.md) - 密码加密总结

## 总结

本系统实现了严格的密钥管理：

✅ **无默认密钥** - 代码和配置文件中不包含任何默认密钥
✅ **强制设置** - 密钥未设置时应用无法启动
✅ **自动化工具** - 提供密钥生成和初始化脚本
✅ **安全存储** - 密钥仅存储在 .env 文件中
✅ **访问控制** - 文件权限限制和版本控制排除
✅ **轮换支持** - 提供密钥轮换流程和工具
✅ **备份策略** - 提供安全的备份和恢复方法

遵循本指南可以确保密钥的安全管理和使用。
