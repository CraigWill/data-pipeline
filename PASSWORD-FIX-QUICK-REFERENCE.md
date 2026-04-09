# 密码修复快速参考

## 问题总结

修复了 docker-compose.yml 中的默认密码和 AES 加密问题。

## 快速修复步骤

```bash
# 1. 测试加密解密
./test-encryption.sh

# 2. 重新加密密码（如果需要）
./encrypt-password.sh "your-password"

# 3. 更新 .env 文件
# DATABASE_PASSWORD=<新的加密密码>

# 4. 重新编译
cd monitor-backend && mvn clean package -DskipTests && cd ..

# 5. 重新构建镜像
docker-compose build monitor-backend --no-cache

# 6. 完全重启
docker-compose down && docker-compose up -d

# 7. 验证
docker-compose ps
docker-compose logs monitor-backend | grep "解密\|Started"
curl http://localhost:5001/actuator/health
```

## 验证清单

- [ ] 加密解密测试通过 (`./test-encryption.sh`)
- [ ] 容器环境变量正确 (`./check-container-env.sh`)
- [ ] 日志显示 "已成功解密"
- [ ] 应用启动成功 "Started UnifiedApplication"
- [ ] 所有容器健康 (`docker-compose ps`)
- [ ] 数据库连接正常 (`curl http://localhost:5001/actuator/health`)

## 常用命令

```bash
# 加密密码
./encrypt-password.sh "password"

# 测试加密解密
./test-encryption.sh

# 检查容器环境变量
./check-container-env.sh

# 查看日志
docker-compose logs monitor-backend --tail=50

# 完全重启
docker-compose down && docker-compose up -d

# 健康检查
curl http://localhost:5001/actuator/health
```

## 相关文档

- [DOCKER-COMPOSE-PASSWORD-FIX-SUMMARY.md](DOCKER-COMPOSE-PASSWORD-FIX-SUMMARY.md) - 完整修复总结
- [REMOVE-DEFAULT-PASSWORDS.md](REMOVE-DEFAULT-PASSWORDS.md) - 移除默认密码
- [KEY-MANAGEMENT-GUIDE.md](KEY-MANAGEMENT-GUIDE.md) - 密钥管理指南
