# 移除配置文件中的默认密码

## 完成时间
2026年3月30日

## 问题描述

在之前的严格密钥管理实施中，虽然已经移除了代码中的默认密钥，但配置文件（application.yml）中仍然存在默认密码 `password`，这违反了安全最佳实践。

## 修复内容

### 修改的文件

已移除以下配置文件中的所有默认密码：

1. **monitor-backend/src/main/resources/application.yml**
   - `spring.datasource.password`: `password` → `${DATABASE_PASSWORD}`
   - `database.password`: `password` → `${DATABASE_PASSWORD}`

2. **monitor-backend/src/main/resources/application-docker.yml**
   - `spring.datasource.password`: `password` → `${DATABASE_PASSWORD}`

3. **src/main/resources/application.yml**
   - `database.password`: `password` → `${DATABASE_PASSWORD}`

4. **src/main/resources/application-docker.yml**
   - `spring.datasource.password`: `password` → `${DATABASE_PASSWORD}`

5. **src/main/resources/application-ha-zookeeper.yml**
   - `database.password`: `password` → `${DATABASE_PASSWORD}`

6. **src/main/resources/application-ha-kubernetes.yml**
   - `database.password`: `password` → `${DATABASE_PASSWORD}`

### 修改前后对比

#### 修改前（不安全）
```yaml
spring:
  datasource:
    password: ${DATABASE_PASSWORD:password}  # ❌ 有默认值
    
database:
  password: password  # ❌ 硬编码密码
```

#### 修改后（安全）
```yaml
spring:
  datasource:
    password: ${DATABASE_PASSWORD}  # ✅ 必须设置环境变量
    
database:
  password: ${DATABASE_PASSWORD}  # ✅ 必须设置环境变量
```

## 安全改进

### 之前的问题

❌ 配置文件中包含默认密码 `password`
❌ 如果环境变量未设置，会使用弱密码
❌ 可能在开发/测试环境使用默认密码

### 现在的改进

✅ 所有配置文件中移除了默认密码
✅ 密码必须通过环境变量 `DATABASE_PASSWORD` 设置
✅ 如果环境变量未设置，应用将无法连接数据库（明确失败）
✅ 强制开发者在所有环境中设置正确的密码

## 影响范围

### 需要设置的环境变量

所有环境（开发、测试、生产）都必须在 `.env` 文件或环境变量中设置：

```bash
DATABASE_PASSWORD=<实际密码或加密后的密码>
```

### 密码格式

支持两种格式：

1. **明文密码**（不推荐，仅用于开发环境）
   ```bash
   DATABASE_PASSWORD=mypassword123
   ```

2. **加密密码**（推荐，用于生产环境）
   ```bash
   # 使用 encrypt-password.sh 加密
   ./encrypt-password.sh "mypassword123"
   
   # 将输出的密文设置到 .env
   DATABASE_PASSWORD=Lek5PkYJ7QflHC1spWlmMw==
   ```

## 验证步骤

### 1. 验证配置文件中无默认密码

```bash
# 搜索所有配置文件
grep -r "password: password" --include="*.yml" --include="*.yaml"

# 应该没有任何输出
```

### 2. 验证环境变量已设置

```bash
# 检查 .env 文件
grep "^DATABASE_PASSWORD=" .env

# 应该看到密码（明文或密文）
```

### 3. 验证应用启动

```bash
# 启动应用
docker-compose up -d monitor-backend

# 检查日志
docker-compose logs monitor-backend | grep -i "started\|error"

# 应该看到 "Started" 消息
```

### 4. 验证数据库连接

```bash
# 测试数据库连接
curl http://localhost:5001/actuator/health

# 应该返回 {"status":"UP"}
```

## 故障排查

### 问题 1: 应用启动失败 - 数据库连接错误

**错误信息**:
```
Cannot create PoolableConnectionFactory
(ORA-01017: invalid username/password; logon denied)
```

**原因**: `DATABASE_PASSWORD` 环境变量未设置或密码错误

**解决方案**:
```bash
# 1. 检查环境变量
grep "^DATABASE_PASSWORD=" .env

# 2. 如果未设置，添加密码
echo "DATABASE_PASSWORD=your_password" >> .env

# 3. 如果使用加密密码，确保 AES_ENCRYPTION_KEY 正确
grep "^AES_ENCRYPTION_KEY=" .env

# 4. 重启服务
docker-compose restart monitor-backend
```

### 问题 2: 密码解密失败

**错误信息**:
```
RuntimeException: AES 解密失败
```

**原因**: 密码是用不同的 AES 密钥加密的

**解决方案**:
```bash
# 1. 使用当前的 AES 密钥重新加密密码
./encrypt-password.sh "your_plain_password"

# 2. 更新 .env 文件
# DATABASE_PASSWORD=<新的加密密码>

# 3. 重启服务
docker-compose restart
```

### 问题 3: Docker 容器无法读取环境变量

**解决方案**:
```bash
# 1. 验证 docker-compose.yml 配置
docker-compose config | grep DATABASE_PASSWORD

# 2. 重新启动（强制重新加载）
docker-compose down
docker-compose up -d

# 3. 验证容器内环境变量
docker-compose exec monitor-backend env | grep DATABASE_PASSWORD
```

## 部署检查清单

### 开发环境

- [x] 已移除所有配置文件中的默认密码
- [x] .env 文件中已设置 DATABASE_PASSWORD
- [x] 应用启动正常
- [x] 数据库连接正常

### 生产环境部署前

- [ ] 使用加密密码（不使用明文）
- [ ] 验证 AES_ENCRYPTION_KEY 已正确设置
- [ ] 测试密码加密/解密
- [ ] 验证数据库连接
- [ ] 限制 .env 文件访问权限（chmod 600）
- [ ] 确保 .env 文件未提交到版本控制

## 最佳实践

### ✅ 应该做的

1. **使用加密密码**（生产环境）
   ```bash
   ./encrypt-password.sh "strong_password"
   ```

2. **限制文件权限**
   ```bash
   chmod 600 .env
   ```

3. **定期轮换密码**
   - 每 90 天更换一次
   - 安全事件后立即更换

4. **使用强密码**
   - 至少 12 个字符
   - 包含大小写字母、数字、特殊字符

5. **分离环境密码**
   - 开发、测试、生产使用不同的密码
   - 不在多个环境共享密码

### ❌ 不应该做的

1. ❌ 在配置文件中硬编码密码
2. ❌ 使用默认密码（如 "password", "123456"）
3. ❌ 将 .env 文件提交到版本控制
4. ❌ 在日志中打印密码
5. ❌ 在多个环境共享密码
6. ❌ 使用弱密码

## 相关文档

- [KEY-MANAGEMENT-GUIDE.md](KEY-MANAGEMENT-GUIDE.md) - 密钥管理指南
- [STRICT-KEY-MANAGEMENT-SUMMARY.md](STRICT-KEY-MANAGEMENT-SUMMARY.md) - 严格密钥管理总结
- [ENV-PASSWORD-ENCRYPTION-GUIDE.md](ENV-PASSWORD-ENCRYPTION-GUIDE.md) - 密码加密指南

## 总结

已成功移除所有配置文件中的默认密码：

✅ **6 个配置文件已修复**
✅ **所有密码配置都使用环境变量**
✅ **无默认密码或回退值**
✅ **强制在所有环境中设置密码**
✅ **支持明文和加密两种格式**

系统现在完全符合安全最佳实践，不存在任何硬编码或默认密码。
