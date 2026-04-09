# 安装 OpenJDK 17 指南

## 当前状态

系统中已安装：
- ✅ Java 11 (Microsoft OpenJDK 11.0.29)
- ❌ Java 17 (未安装)

项目已配置使用 Java 17，需要安装后才能构建。

## 安装方法

### 方法 1: 使用 Homebrew（推荐）

```bash
# 安装 OpenJDK 17
brew install openjdk@17

# 创建符号链接
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-17.jdk

# 验证安装
/usr/libexec/java_home -V
```

### 方法 2: 手动下载安装

1. 访问 [Adoptium](https://adoptium.net/)
2. 选择：
   - Version: 17 (LTS)
   - Operating System: macOS
   - Architecture: ARM64 (M1/M2/M3) 或 x64 (Intel)
3. 下载 `.pkg` 文件
4. 双击安装

### 方法 3: 使用 SDKMAN

```bash
# 安装 SDKMAN
curl -s "https://get.sdkman.io" | bash

# 安装 Java 17
sdk install java 17.0.9-tem

# 设置为默认版本
sdk default java 17.0.9-tem
```

## 验证安装

```bash
# 查看所有已安装的 Java 版本
/usr/libexec/java_home -V

# 应该看到类似输出：
# Matching Java Virtual Machines (2):
#     17.0.x (arm64) "Eclipse Adoptium" - "OpenJDK 17.0.x"
#     11.0.29 (arm64) "Microsoft" - "OpenJDK 11.0.29"

# 设置 JAVA_HOME 为 Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 验证版本
java -version
# 应该显示: openjdk version "17.0.x"
```

## 配置环境变量

### 临时设置（当前终端会话）

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH=$JAVA_HOME/bin:$PATH
```

### 永久设置

#### Bash (~/.bash_profile 或 ~/.bashrc)

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.bash_profile
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bash_profile
source ~/.bash_profile
```

#### Zsh (~/.zshrc)

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.zshrc
source ~/.zshrc
```

## 构建项目

安装 Java 17 后，运行：

```bash
# 1. 构建 Java 项目
./quick-build.sh

# 2. 构建 Docker 镜像
./rebuild-all.sh

# 3. 启动服务
docker-compose up -d
```

## 切换 Java 版本

### 临时切换到 Java 17

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
java -version
```

### 临时切换到 Java 11

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
java -version
```

### 使用 jenv 管理多个 Java 版本

```bash
# 安装 jenv
brew install jenv

# 添加到 shell 配置
echo 'export PATH="$HOME/.jenv/bin:$PATH"' >> ~/.zshrc
echo 'eval "$(jenv init -)"' >> ~/.zshrc
source ~/.zshrc

# 添加 Java 版本
jenv add /Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home
jenv add /Library/Java/JavaVirtualMachines/microsoft-11.jdk/Contents/Home

# 查看可用版本
jenv versions

# 设置全局版本
jenv global 17

# 设置项目版本（在项目目录中）
jenv local 17
```

## 故障排查

### Q1: brew install openjdk@17 很慢

**解决方案**: 使用国内镜像源

```bash
# 设置 Homebrew 镜像
export HOMEBREW_BOTTLE_DOMAIN=https://mirrors.tuna.tsinghua.edu.cn/homebrew-bottles

# 然后安装
brew install openjdk@17
```

### Q2: 符号链接创建失败

**错误**: `Operation not permitted`

**解决方案**: 使用 sudo

```bash
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

### Q3: Maven 仍然使用 Java 11

**解决方案**: 显式设置 JAVA_HOME

```bash
# 方法 1: 在命令前设置
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn clean install

# 方法 2: 导出环境变量
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean install

# 方法 3: 使用 Maven 配置
echo "export JAVA_HOME=\$(/usr/libexec/java_home -v 17)" >> ~/.mavenrc
```

### Q4: Docker 镜像构建失败

**原因**: JAR 文件是用 Java 11 编译的，但 Dockerfile 使用 Java 17

**解决方案**: 重新构建 JAR

```bash
# 清理旧的构建
mvn clean

# 使用 Java 17 重新构建
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn clean install -DskipTests
```

## 临时方案：继续使用 Java 11

如果暂时无法安装 Java 17，可以将项目配置改回 Java 11：

```bash
# 1. 恢复 POM 配置
sed -i '' 's/<java.version>17<\/java.version>/<java.version>11<\/java.version>/g' pom.xml
sed -i '' 's/<maven.compiler.source>17<\/maven.compiler.source>/<maven.compiler.source>11<\/maven.compiler.source>/g' pom.xml
sed -i '' 's/<maven.compiler.target>17<\/maven.compiler.target>/<maven.compiler.target>11<\/maven.compiler.target>/g' pom.xml

# 2. 更新 Dockerfile
find docker -name "Dockerfile*" -exec sed -i '' 's/eclipse-temurin:17-jre/eclipse-temurin:11-jre/g' {} \;
sed -i '' 's/eclipse-temurin:17-jre/eclipse-temurin:11-jre/g' monitor/Dockerfile

# 3. 重新构建
./quick-build.sh
./rebuild-all.sh
```

## 推荐配置

为了项目的长期维护，建议：

1. ✅ 安装 Java 17（LTS 版本，支持到 2029年）
2. ✅ 保留 Java 11（用于其他项目）
3. ✅ 使用 jenv 或环境变量管理多个版本
4. ✅ 在项目 README 中说明 Java 版本要求

## 相关链接

- [Adoptium OpenJDK](https://adoptium.net/)
- [Homebrew OpenJDK](https://formulae.brew.sh/formula/openjdk@17)
- [SDKMAN](https://sdkman.io/)
- [jenv](https://www.jenv.be/)

## 下一步

1. 安装 Java 17
2. 验证安装: `java -version`
3. 构建项目: `./quick-build.sh`
4. 构建镜像: `./rebuild-all.sh`
5. 启动服务: `docker-compose up -d`
