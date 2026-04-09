# 安全认证部署指南

## 概述

本系统已实现完整的安全认证机制，包括：
- JWT Token 认证
- 用户密码 BCrypt 加密（单向）
- 数据源密码 AES 加密（可逆）
- 所有 API 需要登录才能访问（除了登录接口）

## 默认账户

系统初始化时会自动创建默认管理员账户：
- 用户名: `admin`
- 密码: `admin123`

**重要提示**: 生产环境部署后请立即修改默认密码！

## 部署步骤

### 1. 生成安全密钥

生产环境必须生成强密钥：

```bash
# 生成 JWT 密钥（至少 256 位）
openssl rand -base64 64

# 生成 AES 加密密钥（32 字节）
openssl rand -base64 32
```

### 2. 配置环境变量

编辑 `.env` 文件，添加或修改以下配置：

```bash
# JWT 安全配置
JWT_SECRET=your-generated-jwt-secret-key-here
JWT_EXPIRATION=86400000  # 24小时（毫秒）

# AES 密码加密配置
AES_ENCRYPTION_KEY=your-generated-aes-key-here
```

### 3. 加密数据库密码（推荐）

生产环境建议对 `.env` 文件中的数据库密码进行加密：

```bash
# 1. 确保 .env 文件中已设置 AES_ENCRYPTION_KEY
# 2. 使用加密脚本加密密码
./encrypt-password.sh "your-plain-password"

# 3. 复制输出的加密密码到 .env 文件
# 示例输出:
# ✓ 加密成功！
# 原始密码: your-plain-password
# 加密密码: xK8vN2pQ7mL4tR9sW3nY...
# 
# 请将以下内容复制到 .env 文件中:
# DATABASE_PASSWORD=xK8vN2pQ7mL4tR9sW3nY...

# 4. 更新 .env 文件
# DATABASE_PASSWORD=xK8vN2pQ7mL4tR9sW3nY...
```

**注意事项**:
- 加密密码和 `AES_ENCRYPTION_KEY` 必须匹配
- 如果更改 `AES_ENCRYPTION_KEY`，需要重新加密所有密码
- 系统会自动检测密码是明文还是密文，并尝试解密
- 开发环境可以使用明文密码，生产环境必须使用加密密码

### 3. 重新构建和部署

```bash
# 编译后端
cd monitor-backend
mvn clean package -DskipTests

# 编译前端
cd ../monitor/frontend-vue
npm run build

# 重新构建 Docker 镜像
cd ../..
docker-compose build monitor-backend monitor-frontend

# 重启服务
docker-compose up -d monitor-backend monitor-frontend
```

## 使用说明

### 1. 登录

访问系统时会自动跳转到登录页面：`http://localhost:8888/login`

输入默认账户信息：
- 用户名: `admin`
- 密码: `admin123`

登录成功后会自动跳转到首页，Token 会保存在浏览器 localStorage 中。

### 2. API 访问

所有 API 请求都需要在 Header 中携带 Token：

```bash
# 登录获取 Token
curl -X POST http://localhost:5001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 响应示例
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "username": "admin",
    "authorities": [{"authority": "ROLE_ADMIN"}]
  }
}

# 使用 Token 访问 API
curl -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  http://localhost:5001/api/cluster/overview
```

### 3. 登出

点击页面右上角的"退出"按钮，或调用登出 API：

```bash
curl -X POST http://localhost:5001/api/auth/logout \
  -H "Authorization: Bearer your-token-here"
```

### 4. 数据源密码加密

创建或更新数据源时，密码会自动加密存储：

```bash
# 创建数据源（密码会自动加密）
curl -X POST http://localhost:5001/api/datasources \
  -H "Authorization: Bearer your-token-here" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Oracle数据源",
    "host": "localhost",
    "port": 1521,
    "sid": "helowin",
    "username": "finance_user",
    "password": "plain-text-password"
  }'
```

密码在保存到文件时会使用 AES 加密，读取时自动解密。

### 5. 环境变量密码加密

`.env` 文件中的数据库密码也支持加密存储：

```bash
# 使用加密脚本
./encrypt-password.sh "your-database-password"

# 将输出的加密密码复制到 .env 文件
DATABASE_PASSWORD=encrypted-password-here
```

系统会自动检测密码格式：
- 如果是加密密码（Base64 格式），会自动解密后使用
- 如果是明文密码，直接使用
- 解密失败时会回退到使用原密码（兼容明文）

**工作原理**:
1. Java 应用启动时从环境变量读取 `DATABASE_PASSWORD`
2. 尝试使用 AES 解密密码
3. 如果解密成功，使用解密后的密码连接数据库
4. 如果解密失败（明文密码），使用原密码连接数据库

这种设计确保了向后兼容性，同时提供了更高的安全性。

## 安全特性

### 1. JWT Token 认证

- Token 有效期：24 小时（可配置）
- Token 包含用户名和权限信息
- Token 使用 HS256 算法签名
- Token 过期后需要重新登录

### 2. 密码加密

#### 用户密码（BCrypt）
- 单向加密，无法解密
- 每次加密结果不同（加盐）
- 验证时使用 BCrypt 比对

#### 数据源密码（AES）
- 可逆加密，用于连接数据库
- 使用 AES 算法
- 密钥从环境变量读取

### 3. API 访问控制

- 公开端点：`/api/auth/**`（登录、登出）
- 受保护端点：`/api/**`（所有其他 API）
- 未认证访问返回 401 Unauthorized

### 4. 前端集成

- 自动添加 Token 到请求头
- 401 响应自动跳转登录页
- Token 保存在 localStorage
- 登出时清除 Token

## 安全建议

### 生产环境必须做的事

1. **修改默认密码**
   ```bash
   # 首次登录后立即修改密码
   # 目前需要通过代码修改，后续版本会提供修改密码功能
   ```

2. **使用强密钥**
   - JWT_SECRET 至少 256 位
   - AES_ENCRYPTION_KEY 必须是 32 字节
   - 使用随机生成的密钥，不要使用默认值

3. **密钥管理**
   - 不要将密钥提交到版本控制系统
   - 使用环境变量或密钥管理服务（如 AWS KMS、阿里云 KMS）
   - 定期轮换密钥

4. **启用 HTTPS**
   ```nginx
   # 在 nginx 中配置 SSL
   server {
       listen 443 ssl;
       ssl_certificate /path/to/cert.pem;
       ssl_certificate_key /path/to/key.pem;
       ...
   }
   ```

5. **配置防火墙**
   - 只开放必要的端口（80、443、8888）
   - 限制 5001 端口只能从内网访问
   - 使用 iptables 或云服务商的安全组

6. **监控和审计**
   - 记录所有登录尝试
   - 监控异常访问模式
   - 定期审查访问日志

### 可选的安全增强

1. **Token 刷新机制**
   - 实现 Refresh Token
   - 缩短 Access Token 有效期

2. **登录失败限制**
   - 限制登录失败次数
   - 实现账户锁定机制

3. **双因素认证（2FA）**
   - 添加 TOTP 支持
   - 短信验证码

4. **密码策略**
   - 强制密码复杂度
   - 定期强制修改密码
   - 密码历史记录

5. **会话管理**
   - 实现单点登录（SSO）
   - 支持多设备登录管理
   - 强制登出功能

## 故障排查

### 1. 登录失败

**问题**: 输入正确的用户名密码仍然登录失败

**解决方案**:
```bash
# 检查后端日志
docker-compose logs monitor-backend | grep -i "login\|auth"

# 确认用户服务是否正常初始化
docker-compose logs monitor-backend | grep "默认管理员账户已创建"
```

### 2. Token 验证失败

**问题**: API 返回 401 Unauthorized

**解决方案**:
```bash
# 检查 Token 是否过期
# 检查 JWT_SECRET 是否一致
# 确认 Token 格式正确（Bearer <token>）

# 查看后端日志
docker-compose logs monitor-backend | grep -i "jwt\|token"
```

### 3. 密码解密失败

**问题**: 连接数据源时密码解密失败

**解决方案**:
```bash
# 确认 AES_ENCRYPTION_KEY 没有改变
# 如果改变了密钥，需要重新创建所有数据源

# 查看错误日志
docker-compose logs monitor-backend | grep -i "decrypt\|aes"
```

### 4. 前端无法访问 API

**问题**: 前端显示网络错误

**解决方案**:
```bash
# 检查浏览器控制台（F12）
# 查看 Network 标签，确认请求是否发送
# 检查 Authorization Header 是否正确

# 清除浏览器缓存和 localStorage
localStorage.clear()
location.reload()
```

## 测试

### 1. 测试登录

```bash
# 测试登录
curl -X POST http://localhost:5001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 预期响应
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "username": "admin"
  }
}
```

### 2. 测试受保护的 API

```bash
# 不带 Token（应该返回 401）
curl http://localhost:5001/api/cluster/overview

# 带 Token（应该返回正常数据）
curl -H "Authorization: Bearer <your-token>" \
  http://localhost:5001/api/cluster/overview
```

### 3. 测试密码加密

```bash
# 创建数据源
TOKEN="your-token-here"
curl -X POST http://localhost:5001/api/datasources \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试数据源",
    "host": "localhost",
    "port": 1521,
    "sid": "test",
    "username": "user",
    "password": "password123"
  }'

# 检查配置文件，密码应该是加密的
cat monitor/config/datasources/*.json
# 应该看到类似: "password": "encrypted-base64-string"
```

## 后续改进

1. **用户管理界面**
   - 添加用户列表页面
   - 支持创建、编辑、删除用户
   - 修改密码功能

2. **角色权限管理**
   - 实现 RBAC（基于角色的访问控制）
   - 不同角色有不同权限
   - 细粒度的 API 权限控制

3. **数据库存储**
   - 将用户信息存储到数据库
   - 支持更多用户
   - 持久化用户数据

4. **OAuth2/OIDC 集成**
   - 支持第三方登录（GitHub、Google 等）
   - 企业 SSO 集成
   - LDAP/AD 集成

5. **审计日志**
   - 记录所有用户操作
   - 登录历史
   - API 访问日志

## 参考文档

- [Spring Security 官方文档](https://spring.io/projects/spring-security)
- [JWT 官方网站](https://jwt.io/)
- [OWASP 安全指南](https://owasp.org/)
- [BCrypt 算法说明](https://en.wikipedia.org/wiki/Bcrypt)
- [AES 加密标准](https://en.wikipedia.org/wiki/Advanced_Encryption_Standard)
