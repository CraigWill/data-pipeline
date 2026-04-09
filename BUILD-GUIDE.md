# 项目构建指南

## 多模块项目重构完成

项目已成功重构为多模块结构：

```
realtime-data-pipeline-parent/
├── pom.xml (父 POM)
├── flink-jobs/ (Flink CDC 任务)
│   ├── pom.xml
│   └── src/main/java/com/realtime/pipeline/
└── monitor-backend/ (Spring Boot 监控后端)
    ├── pom.xml
    └── src/main/java/com/realtime/monitor/
```

## Java 版本要求

项目配置使用 Java 17，但系统当前有以下 Java 版本：
- Java 11 (Microsoft OpenJDK 11.0.29) - 已安装
- Java 25 (Homebrew OpenJDK 25.0.1) - 当前默认

## 方案 1: 安装 OpenJDK 17（推荐）

### 使用 Homebrew 安装

```bash
# 安装 OpenJDK 17
brew install openjdk@17

# 创建符号链接
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk

# 设置 JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 验证版本
java -version
```

### 配置 Maven 使用 Java 17

在 `~/.mavenrc` 中添加：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

或在构建时指定：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn clean install -DskipTests
```

## 方案 2: 使用 Java 11 构建（临时方案）

如果 OpenJDK 17 安装较慢，可以临时使用 Java 11：

### 1. 修改 POM 配置

```bash
# 将所有 POM 文件中的 Java 版本改为 11
sed -i '' 's/<java.version>17<\/java.version>/<java.version>11<\/java.version>/g' pom.xml
sed -i '' 's/<maven.compiler.source>17<\/maven.compiler.source>/<maven.compiler.source>11<\/maven.compiler.source>/g' pom.xml
sed -i '' 's/<maven.compiler.target>17<\/maven.compiler.target>/<maven.compiler.target>11<\/maven.compiler.target>/g' pom.xml
```

### 2. 使用 Java 11 构建

```bash
# 设置 JAVA_HOME 为 Java 11
export JAVA_HOME=$(/usr/libexec/java_home -v 11)

# 验证版本
java -version

# 构建项目
mvn clean install -DskipTests
```

### 3. 更新 Dockerfile

将所有 Dockerfile 中的基础镜像改为 Java 11：

```dockerfile
# 从
FROM eclipse-temurin:17-jre

# 改为
FROM eclipse-temurin:11-jre
```

## 构建步骤

### 1. 清理并构建所有模块

```bash
# 使用 Java 17（推荐）
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn clean install -DskipTests

# 或使用 Java 11（临时）
JAVA_HOME=$(/usr/libexec/java_home -v 11) mvn clean install -DskipTests
```

### 2. 验证构建产物

```bash
# 检查 Flink Jobs JAR
ls -lh flink-jobs/target/flink-jobs-*.jar

# 检查 Monitor Backend JAR
ls -lh monitor-backend/target/monitor-backend-*.jar
```

预期输出：
```
flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar (~100MB)
monitor-backend/target/monitor-backend-1.0.0-SNAPSHOT.jar (~50MB)
```

### 3. 构建 Docker 镜像

```bash
# 使用国内镜像源（推荐）
./build-flink-images-cn.sh

# 或使用标准版本
./build-flink-images.sh
```

### 4. 启动服务

```bash
# 启动所有服务
./start.sh

# 或使用 docker-compose
docker-compose up -d
```

## 常见问题

### Q1: Maven 编译错误 "invalid target release: 17"

**原因**: 当前 Java 版本不支持编译 Java 17 代码

**解决方案**:
1. 安装 Java 17: `brew install openjdk@17`
2. 或临时使用 Java 11（修改 POM 配置）

### Q2: Homebrew 下载速度慢

**解决方案**:
1. 使用国内镜像源：
   ```bash
   export HOMEBREW_BOTTLE_DOMAIN=https://mirrors.tuna.tsinghua.edu.cn/homebrew-bottles
   ```

2. 或手动下载 OpenJDK 17:
   - 访问 https://adoptium.net/
   - 下载 Temurin 17 (LTS) for macOS ARM64
   - 安装 .pkg 文件

### Q3: Docker 构建失败

**检查清单**:
1. Maven 构建是否成功
2. JAR 文件是否存在于正确路径
3. Dockerfile 中的 JAR 路径是否正确
4. 使用国内镜像源版本: `./build-flink-images-cn.sh`

### Q4: 如何只构建某个模块

```bash
# 只构建 Flink Jobs
mvn clean install -pl flink-jobs -DskipTests

# 只构建 Monitor Backend
mvn clean install -pl monitor-backend -DskipTests
```

## 项目文件变更

### 新增文件
- `pom.xml` (父 POM，替换原文件)
- `flink-jobs/pom.xml`
- `flink-jobs/src/` (Flink 任务代码)
- `monitor-backend/pom.xml`
- `monitor-backend/src/` (监控后端代码)
- `migrate-to-multimodule.sh` (迁移脚本)
- `MULTI-MODULE-MIGRATION.md` (迁移文档)
- `BUILD-GUIDE.md` (本文档)

### 修改文件
- `docker/jobmanager/Dockerfile` - JAR 路径更新
- `docker/jobmanager/Dockerfile.cn` - JAR 路径更新
- `docker/taskmanager/Dockerfile` - JAR 路径更新
- `docker/taskmanager/Dockerfile.cn` - JAR 路径更新
- `monitor/Dockerfile` - JAR 路径更新
- `build-flink-images.sh` - JAR 路径更新
- `build-flink-images-cn.sh` - JAR 路径更新

### 备份文件
- `pom.xml.backup` - 原始 POM 文件
- `*.bak` - Dockerfile 和脚本的备份

## 下一步

1. 选择 Java 版本方案（17 或 11）
2. 执行 Maven 构建
3. 构建 Docker 镜像
4. 启动服务并测试

## 技术支持

如有问题，请查看：
- `MULTI-MODULE-MIGRATION.md` - 多模块迁移详细文档
- `CHINA-MIRRORS.md` - 国内镜像源配置
- `START-GUIDE.md` - 启动脚本使用指南
