# 环境变量快速参考

## 快速配置（3 步）

```bash
# 1. 配置系统环境变量
./setup-env-vars.sh

# 2. 重新加载配置（在当前终端立即生效）
source ~/.zshrc

# 3. 验证配置
./verify-env.sh
```

## 常用命令

### 查看环境变量

```bash
# 查看所有项目相关的环境变量
env | grep -E "JWT_SECRET|AES_ENCRYPTION_KEY|DATABASE"

# 查看单个变量
echo $JWT_SECRET
echo $DATABASE_PASSWORD

# 查看变量长度（验证是否完整）
echo ${#JWT_SECRET}  # 应该是 88
echo ${#AES_ENCRYPTION_KEY}  # 应该是 44
```

### 重新加载配置

```bash
# Zsh
source ~/.zshrc

# Bash
source ~/.bashrc

# 或者重启终端
```

### 更新环境变量

```bash
# 1. 更新 .env 文件
nano .env

# 2. 重新运行配置脚本
./setup-env-vars.sh

# 3. 重新加载
source ~/.zshrc
```

### 移除环境变量

```bash
# 1. 编辑配置文件
nano ~/.zshrc

# 2. 删除 "# 实时数据管道系统 - 环境变量" 部分

# 3. 重新加载
source ~/.zshrc
```

## 故障排查

### 环境变量未生效

```bash
# 检查配置文件
cat ~/.zshrc | grep JWT_SECRET

# 重新加载
source ~/.zshrc

# 验证
echo $JWT_SECRET
```

### 新终端窗口中不可用

```bash
# 确认配置文件位置
echo $SHELL  # 查看使用的 shell

# Zsh 应该使用 ~/.zshrc
# Bash 应该使用 ~/.bashrc

# 打开新终端测试
echo $JWT_SECRET
```

### 应用仍然报错

```bash
# 1. 验证环境变量
./verify-env.sh

# 2. 如果使用 IDE，重启 IDE

# 3. 如果使用 Docker，检查 docker-compose.yml
docker-compose config | grep JWT_SECRET

# 4. 验证容器内环境变量
docker-compose exec monitor-backend env | grep JWT_SECRET
```

## 不同场景

### Docker Compose（推荐）

```bash
# 不需要配置系统环境变量
# Docker Compose 自动加载 .env 文件
docker-compose up -d
```

### 直接运行 JAR

```bash
# 需要系统环境变量
./setup-env-vars.sh
source ~/.zshrc
java -jar monitor-backend/target/monitor-backend-1.0.0-SNAPSHOT.jar
```

### Maven 构建

```bash
# 需要系统环境变量
./setup-env-vars.sh
source ~/.zshrc
mvn clean package
```

### IDE 运行

```bash
# 选项 1: 配置系统环境变量
./setup-env-vars.sh
# 重启 IDE

# 选项 2: 在 IDE 中配置环境变量
# IntelliJ: Run → Edit Configurations → Environment variables
# Eclipse: Run → Run Configurations → Environment
```

## 环境变量列表

### 必需

- `JWT_SECRET` - JWT Token 签名密钥（88 字符）
- `AES_ENCRYPTION_KEY` - AES 加密密钥（44 字符）
- `DATABASE_PASSWORD` - 数据库密码（明文或密文）

### 可选

- `DATABASE_HOST` - 数据库主机（默认: localhost）
- `DATABASE_PORT` - 数据库端口（默认: 1521）
- `DATABASE_USERNAME` - 数据库用户名（默认: finance_user）
- `DATABASE_SID` - Oracle SID（默认: helowin）
- `JWT_EXPIRATION` - Token 过期时间（默认: 86400000）

## 安全提示

```bash
# 限制配置文件权限
chmod 600 ~/.zshrc
chmod 600 .env

# 验证权限
ls -l ~/.zshrc  # 应该显示 -rw-------

# 不要将配置文件提交到 Git
# .gitignore 已包含 .env
```

## 相关文档

- [SYSTEM-ENV-SETUP-GUIDE.md](SYSTEM-ENV-SETUP-GUIDE.md) - 完整配置指南
- [KEY-MANAGEMENT-GUIDE.md](KEY-MANAGEMENT-GUIDE.md) - 密钥管理指南
- [REMOVE-DEFAULT-PASSWORDS.md](REMOVE-DEFAULT-PASSWORDS.md) - 移除默认密码总结

## 帮助

```bash
# 查看配置脚本帮助
./setup-env-vars.sh --help

# 验证环境变量
./verify-env.sh

# 查看所有环境变量
env | sort
```
