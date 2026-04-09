# 密码加密快速参考

## 快速开始

### 1. 加密密码
```bash
./encrypt-password.sh "your-password"
```

### 2. 复制到 .env
```bash
DATABASE_PASSWORD=<encrypted-output>
```

### 3. 重启服务
```bash
docker-compose restart
```

## 常用命令

### 生成 AES 密钥
```bash
openssl rand -base64 32
```

### 检查密钥长度
```bash
echo -n "your-key" | wc -c  # 必须是 16、24 或 32
```

### 加密多个密码
```bash
./encrypt-password.sh "db-password"
./encrypt-password.sh "api-key"
./encrypt-password.sh "admin-password"
```

### 查看当前配置
```bash
grep -E "AES_ENCRYPTION_KEY|DATABASE_PASSWORD" .env
```

## 配置示例

### 开发环境
```bash
AES_ENCRYPTION_KEY=your-32-byte-aes-key-here-123456
DATABASE_PASSWORD=plain-text-password  # 明文
```

### 生产环境
```bash
AES_ENCRYPTION_KEY=<strong-random-key>
DATABASE_PASSWORD=V+uUYS1hqMh0uvC7JwxwIA==  # 加密
```

## 故障排查

### 问题：未找到 AES_ENCRYPTION_KEY
```bash
# 在 .env 中添加
AES_ENCRYPTION_KEY=your-32-byte-aes-key-here-123456
```

### 问题：Invalid AES key length
```bash
# 确保密钥是 32 字节
echo -n "your-32-byte-aes-key-here-123456" | wc -c
```

### 问题：数据库连接失败
```bash
# 重新加密密码
./encrypt-password.sh "correct-password"
# 更新 .env
# 重启服务
docker-compose restart
```

## 安全检查清单

- [ ] 生成强随机 AES 密钥
- [ ] 加密所有敏感密码
- [ ] 限制 .env 文件权限 (chmod 600)
- [ ] 不要提交 .env 到版本控制
- [ ] 定期轮换密钥（每 90 天）
- [ ] 使用密钥管理服务（生产环境）

## 相关文档

- [ENV-PASSWORD-ENCRYPTION-GUIDE.md](ENV-PASSWORD-ENCRYPTION-GUIDE.md) - 完整指南
- [SECURITY-DEPLOYMENT-GUIDE.md](SECURITY-DEPLOYMENT-GUIDE.md) - 部署指南
