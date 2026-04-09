# 系统环境变量配置指南

## 概述

本指南说明如何将项目的环境变量配置到系统级别，使其在所有终端会话中可用。

## 为什么需要系统环境变量？

### .env 文件的局限性

- ✅ 适用于 Docker Compose（自动加载）
- ✅ 适用于某些应用框架
- ❌ 不适用于直接运行的 Java 应用
- ❌ 不适用于 Maven 构建
- ❌ 不适用于 IDE 运行配置

### 系统环境变量的优势

- ✅ 所有终端会话自动可用
- ✅ IDE 可以直接读取
- ✅ Maven 构建可以使用
- ✅ 直接运行 JAR 文件可以使用
- ✅ 脚本和工具可以访问

## 快速配置

### 方法 1: 使用自动化脚本（推荐）

```bash
# 1. 确保 .env 文件已配置
cat .env | grep -E "JWT_SECRET|AES_ENCRYPTION_KEY|DATABASE_PASSWORD"

# 2. 运行配置脚本
./setup-env-vars.sh

# 3. 重新加载配置（在当前终端立即生效）
source ~/.zshrc  # zsh
# 或
source ~/.bashrc  # bash

# 4. 验证环境变量
echo $JWT_SECRET
echo $DATABASE_PASSWORD
```

### 方法 2: 手动配置

#### 对于 Zsh（macOS 默认）

```bash
# 1. 编辑配置文件
nano ~/.zshrc

# 2. 添加以下内容到文件末尾
export JWT_SECRET="<从 .env 复制>"
export AES_ENCRYPTION_KEY="<从 .env 复制>"
export DATABASE_HOST="host.docker.internal"
export DATABASE_PORT="1521"
export DATABASE_USERNAME="finance_user"
export DATABASE_PASSWORD="<从 .env 复制>"
export DATABASE_SID="helowin"

# 3. 保存并退出（Ctrl+O, Enter, Ctrl+X）

# 4. 重新加载配置
source ~/.zshrc

# 5. 验证
echo $JWT_SECRET
```

#### 对于 Bash

```bash
# 1. 编辑配置文件
nano ~/.bashrc

# 2. 添加环境变量（同上）

# 3. 重新加载配置
source ~/.bashrc
```

## 配置文件说明

### Zsh 配置文件

- **~/.zshrc**: 交互式 shell 配置（推荐）
- **~/.zprofile**: 登录 shell 配置

### Bash 配置文件

- **~/.bashrc**: 交互式 shell 配置（推荐）
- **~/.bash_profile**: 登录 shell 配置
- **~/.profile**: 通用配置

### 推荐配置位置

- **开发环境**: `~/.zshrc` 或 `~/.bashrc`（每次打开终端都加载）
- **服务器环境**: `~/.profile`（登录时加载）

## 环境变量列表

### 必需的环境变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| JWT_SECRET | JWT Token 签名密钥 | xGTMWGPrwsj9s1DtwQuQhl0GfLaetGcy... |
| AES_ENCRYPTION_KEY | AES 加密密钥 | E5TDKE7NvyphzdaW0SIUfAhZl2v9sRnj... |
| DATABASE_PASSWORD | 数据库密码（明文或密文） | Lek5PkYJ7QflHC1spWlmMw== |

### 可选的环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| DATABASE_HOST | 数据库主机 | localhost |
| DATABASE_PORT | 数据库端口 | 1521 |
| DATABASE_USERNAME | 数据库用户名 | finance_user |
| DATABASE_SID | Oracle SID | helowin |
| JWT_EXPIRATION | Token 过期时间（毫秒） | 86400000 (24小时) |

## 验证配置

### 1. 验证环境变量已设置

```bash
# 检查所有关键变量
env | grep -E "JWT_SECRET|AES_ENCRYPTION_KEY|DATABASE"

# 或单独检查
echo $JWT_SECRET
echo $AES_ENCRYPTION_KEY
echo $DATABASE_PASSWORD
```

### 2. 验证新终端会话

```bash
# 打开新终端窗口，然后运行
echo $JWT_SECRET

# 应该显示密钥值
```

### 3. 验证应用可以读取

```bash
# 启动应用（不使用 Docker Compose）
cd monitor-backend
mvn spring-boot:run

# 检查日志，应该没有密钥未设置的错误
```

## 安全注意事项

### ⚠️ 重要警告

1. **配置文件包含敏感信息**
   - ~/.zshrc 或 ~/.bashrc 现在包含密钥
   - 必须限制文件权限

2. **不要共享配置文件**
   - 不要将配置文件提交到版本控制
   - 不要通过不安全的渠道传输

3. **定期轮换密钥**
   - 更新 .env 文件后需要重新运行配置脚本
   - 或手动更新配置文件中的值

### 🔒 安全措施

```bash
# 1. 限制配置文件权限
chmod 600 ~/.zshrc  # 或 ~/.bashrc

# 2. 验证权限
ls -l ~/.zshrc

# 应该显示: -rw------- 1 user group ... .zshrc

# 3. 检查文件内容（确保密钥不会被意外显示）
# 不要在 .zshrc 中使用 echo 打印这些变量
```

## 不同场景的配置方法

### 场景 1: 本地开发（推荐使用 Docker Compose）

Docker Compose 会自动加载 .env 文件，不需要配置系统环境变量：

```bash
# 直接启动，自动加载 .env
docker-compose up -d
```

### 场景 2: 本地开发（直接运行 JAR）

需要配置系统环境变量：

```bash
# 1. 配置系统环境变量
./setup-env-vars.sh
source ~/.zshrc

# 2. 运行应用
java -jar monitor-backend/target/monitor-backend-1.0.0-SNAPSHOT.jar
```

### 场景 3: IDE 运行（IntelliJ IDEA / Eclipse）

#### 选项 A: 使用系统环境变量

```bash
# 1. 配置系统环境变量
./setup-env-vars.sh
source ~/.zshrc

# 2. 重启 IDE（使其读取新的环境变量）

# 3. 在 IDE 中直接运行
```

#### 选项 B: 在 IDE 中配置环境变量

**IntelliJ IDEA**:
1. Run → Edit Configurations
2. 选择你的运行配置
3. Environment variables → 点击 📁 图标
4. 添加环境变量：
   - JWT_SECRET=...
   - AES_ENCRYPTION_KEY=...
   - DATABASE_PASSWORD=...

**Eclipse**:
1. Run → Run Configurations
2. 选择你的运行配置
3. Environment 标签页
4. 添加环境变量

### 场景 4: CI/CD 环境

在 CI/CD 平台中配置环境变量（不使用 .env 文件）：

**GitHub Actions**:
```yaml
env:
  JWT_SECRET: ${{ secrets.JWT_SECRET }}
  AES_ENCRYPTION_KEY: ${{ secrets.AES_ENCRYPTION_KEY }}
  DATABASE_PASSWORD: ${{ secrets.DATABASE_PASSWORD }}
```

**GitLab CI**:
```yaml
variables:
  JWT_SECRET: $JWT_SECRET
  AES_ENCRYPTION_KEY: $AES_ENCRYPTION_KEY
  DATABASE_PASSWORD: $DATABASE_PASSWORD
```

### 场景 5: 生产服务器

#### 选项 A: Systemd 服务

```ini
# /etc/systemd/system/monitor-backend.service
[Service]
Environment="JWT_SECRET=..."
Environment="AES_ENCRYPTION_KEY=..."
Environment="DATABASE_PASSWORD=..."
EnvironmentFile=/opt/app/.env
```

#### 选项 B: 使用密钥管理服务

```bash
# AWS Secrets Manager
export JWT_SECRET=$(aws secretsmanager get-secret-value \
  --secret-id prod/jwt-secret \
  --query SecretString \
  --output text)

# 阿里云 KMS
export JWT_SECRET=$(aliyun kms Decrypt \
  --CiphertextBlob <encrypted-value> \
  --query Plaintext \
  --output text | base64 -d)
```

## 移除环境变量

如果需要移除系统环境变量：

```bash
# 1. 编辑配置文件
nano ~/.zshrc  # 或 ~/.bashrc

# 2. 删除环境变量配置块
# 找到并删除 "# 实时数据管道系统 - 环境变量" 部分

# 3. 保存并重新加载
source ~/.zshrc

# 4. 验证已移除
echo $JWT_SECRET  # 应该为空
```

## 故障排查

### 问题 1: 环境变量未生效

**症状**: `echo $JWT_SECRET` 返回空

**解决方案**:
```bash
# 1. 检查配置文件
cat ~/.zshrc | grep JWT_SECRET

# 2. 重新加载配置
source ~/.zshrc

# 3. 如果还是不行，检查是否有语法错误
zsh -n ~/.zshrc  # zsh
bash -n ~/.bashrc  # bash
```

### 问题 2: 新终端窗口中环境变量不可用

**原因**: 配置文件未被自动加载

**解决方案**:
```bash
# 确保使用正确的配置文件
# Zsh: ~/.zshrc
# Bash: ~/.bashrc

# 如果使用 ~/.bash_profile，需要在其中添加
if [ -f ~/.bashrc ]; then
    source ~/.bashrc
fi
```

### 问题 3: 多行值被截断

**症状**: JWT_SECRET 值不完整

**原因**: 变量值包含换行符

**解决方案**:
```bash
# 使用引号包裹值
export JWT_SECRET="xGTMWGPrwsj9s1DtwQuQhl0GfLaetGcy0KXMAeaKIEIgv46z+SM3sg4+4q1GIlX2ApDK5GUw4wbEFCUn1Aa8LQ=="

# 或使用单行格式（移除换行符）
JWT_SECRET=$(grep "^JWT_SECRET=" .env | cut -d'=' -f2- | tr -d '\n')
```

### 问题 4: 应用仍然报错密钥未设置

**解决方案**:
```bash
# 1. 验证环境变量在当前 shell 中可用
env | grep JWT_SECRET

# 2. 如果使用 IDE，重启 IDE

# 3. 如果使用 Docker，确保 docker-compose.yml 配置正确
docker-compose config | grep JWT_SECRET

# 4. 验证容器内环境变量
docker-compose exec monitor-backend env | grep JWT_SECRET
```

## 最佳实践

### ✅ 推荐做法

1. **使用自动化脚本**
   - 减少人为错误
   - 自动备份配置文件
   - 统一配置格式

2. **限制文件权限**
   ```bash
   chmod 600 ~/.zshrc
   chmod 600 .env
   ```

3. **定期同步**
   - .env 文件更新后重新运行配置脚本
   - 或使用符号链接（不推荐，安全风险）

4. **分离环境**
   - 开发环境使用 .env 文件
   - 生产环境使用密钥管理服务

5. **文档化**
   - 记录哪些变量是必需的
   - 记录变量的用途和格式

### ❌ 避免做法

1. ❌ 在全局配置文件中存储生产密钥
2. ❌ 将配置文件提交到版本控制
3. ❌ 在多个用户之间共享配置文件
4. ❌ 使用 777 权限
5. ❌ 在配置文件中使用 echo 打印密钥

## 不同环境的配置策略

### 开发环境（本地）

```bash
# 使用 .env 文件 + Docker Compose
docker-compose up -d

# 或配置系统环境变量（用于 IDE 和直接运行）
./setup-env-vars.sh
```

### 测试环境

```bash
# 使用独立的 .env.test 文件
cp .env.example .env.test
# 编辑 .env.test 设置测试环境的值

# 加载测试环境变量
export $(cat .env.test | xargs)
```

### 生产环境

```bash
# 选项 1: 使用密钥管理服务（推荐）
# AWS Secrets Manager, 阿里云 KMS, HashiCorp Vault

# 选项 2: 使用 Systemd 环境文件
# /etc/systemd/system/monitor-backend.service
[Service]
EnvironmentFile=/opt/app/.env

# 选项 3: 使用 Kubernetes Secrets
kubectl create secret generic app-secrets \
  --from-literal=JWT_SECRET="..." \
  --from-literal=AES_ENCRYPTION_KEY="..." \
  --from-literal=DATABASE_PASSWORD="..."
```

## 配置验证

### 验证脚本

创建一个验证脚本 `verify-env.sh`:

```bash
#!/bin/bash
# 验证环境变量配置

echo "验证环境变量配置..."
echo ""

# 必需的变量
REQUIRED_VARS=(
    "JWT_SECRET"
    "AES_ENCRYPTION_KEY"
    "DATABASE_PASSWORD"
)

# 可选的变量
OPTIONAL_VARS=(
    "DATABASE_HOST"
    "DATABASE_PORT"
    "DATABASE_USERNAME"
    "DATABASE_SID"
)

# 检查必需变量
echo "必需的环境变量:"
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        echo "  ❌ $var: 未设置"
    else
        # 只显示前 20 个字符
        value="${!var}"
        echo "  ✓ $var: ${value:0:20}..."
    fi
done

echo ""
echo "可选的环境变量:"
for var in "${OPTIONAL_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        echo "  ⚠ $var: 未设置（将使用默认值）"
    else
        echo "  ✓ $var: ${!var}"
    fi
done

echo ""
echo "验证完成"
```

### 运行验证

```bash
chmod +x verify-env.sh
./verify-env.sh
```

## 常见问题

### Q1: 为什么 Docker Compose 不需要配置系统环境变量？

A: Docker Compose 会自动读取项目根目录的 .env 文件，并将其中的变量传递给容器。

### Q2: 系统环境变量和 .env 文件有什么区别？

A: 
- **系统环境变量**: 在 shell 配置文件中设置，所有进程可见
- **.env 文件**: 项目级配置，需要应用或工具显式加载

### Q3: 如何在不同项目之间隔离环境变量？

A: 
- 使用项目特定的前缀（如 `PIPELINE_JWT_SECRET`）
- 使用 direnv 工具（自动加载项目目录的 .envrc）
- 在 IDE 中为每个项目配置独立的运行配置

### Q4: 密钥轮换后需要更新系统环境变量吗？

A: 是的，需要：
```bash
# 1. 更新 .env 文件
./init-keys.sh

# 2. 更新系统环境变量
./setup-env-vars.sh

# 3. 重新加载配置
source ~/.zshrc
```

### Q5: 如何在生产环境中安全地配置环境变量？

A: 推荐使用密钥管理服务：
- AWS Secrets Manager
- 阿里云 KMS
- HashiCorp Vault
- Kubernetes Secrets

不要在生产服务器的 shell 配置文件中存储密钥。

## 工具推荐

### direnv（项目级环境变量管理）

```bash
# 安装 direnv
brew install direnv

# 配置 shell hook
echo 'eval "$(direnv hook zsh)"' >> ~/.zshrc

# 在项目目录创建 .envrc
echo 'dotenv' > .envrc

# 允许加载
direnv allow

# 现在进入项目目录会自动加载 .env 文件
```

### pass（密码管理器）

```bash
# 安装 pass
brew install pass

# 初始化
pass init <gpg-key-id>

# 存储密钥
pass insert realtime-pipeline/jwt-secret
pass insert realtime-pipeline/aes-key

# 读取密钥
export JWT_SECRET=$(pass show realtime-pipeline/jwt-secret)
```

## 相关文档

- [KEY-MANAGEMENT-GUIDE.md](KEY-MANAGEMENT-GUIDE.md) - 密钥管理指南
- [STRICT-KEY-MANAGEMENT-SUMMARY.md](STRICT-KEY-MANAGEMENT-SUMMARY.md) - 严格密钥管理总结
- [ENV-PASSWORD-ENCRYPTION-GUIDE.md](ENV-PASSWORD-ENCRYPTION-GUIDE.md) - 密码加密指南

## 总结

系统环境变量配置提供了一种在所有终端会话和应用中使用环境变量的方法。

**推荐配置方式**:
- **Docker Compose 部署**: 使用 .env 文件（无需系统环境变量）
- **本地开发/IDE**: 配置系统环境变量
- **生产环境**: 使用密钥管理服务

**快速开始**:
```bash
./setup-env-vars.sh
source ~/.zshrc
./verify-env.sh
```
