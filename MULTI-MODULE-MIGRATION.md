# 多模块项目重构指南

## 概述

将原单模块项目重构为多模块 Maven 项目，实现 Flink 任务和 Monitor Backend 的分离构建。

## 项目结构

### 重构前（单模块）

```
realtime-data-pipeline/
├── pom.xml (包含所有依赖)
├── src/
│   └── main/
│       ├── java/
│       │   └── com/realtime/
│       │       ├── pipeline/      # Flink 任务
│       │       ├── monitor/       # 监控后端
│       │       └── UnifiedApplication.java
│       └── resources/
│           ├── application*.yml   # Spring Boot 配置
│           └── log4j2.xml        # Flink 日志配置
└── target/
    └── realtime-data-pipeline-1.0.0-SNAPSHOT.jar (146MB)
```

### 重构后（多模块）

```
realtime-data-pipeline-parent/
├── pom.xml (父 POM，依赖管理)
│
├── flink-jobs/                    # Flink CDC 任务模块
│   ├── pom.xml
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/realtime/pipeline/
│   │       │       └── CdcJobMain.java
│   │       └── resources/
│   │           └── log4j2.xml
│   └── target/
│       └── flink-jobs-1.0.0-SNAPSHOT.jar (~100MB)
│
└── monitor-backend/               # Spring Boot 监控后端模块
    ├── pom.xml
    ├── src/
    │   └── main/
    │       ├── java/
    │       │   └── com/realtime/
    │       │       ├── monitor/
    │       │       └── UnifiedApplication.java
    │       └── resources/
    │           └── application*.yml
    └── target/
        └── monitor-backend-1.0.0-SNAPSHOT.jar (~50MB)
```

## 模块说明

### 1. 父 POM (realtime-data-pipeline-parent)

- **作用**: 统一管理依赖版本和插件配置
- **packaging**: `pom`
- **modules**: `flink-jobs`, `monitor-backend`

### 2. flink-jobs 模块

- **作用**: Flink CDC 任务和流处理作业
- **主类**: `com.realtime.pipeline.CdcJobMain`
- **依赖**:
  - Flink Core (streaming-java, clients, runtime)
  - Flink CDC Connector (oracle-cdc)
  - Oracle JDBC Driver
  - Flink File Connectors
  - Log4j2
- **构建**: Maven Shade Plugin 生成 Fat JAR
- **输出**: `flink-jobs-1.0.0-SNAPSHOT.jar`

### 3. monitor-backend 模块

- **作用**: Spring Boot 监控后端服务
- **主类**: `com.realtime.UnifiedApplication`
- **依赖**:
  - Spring Boot Web
  - Spring Boot Actuator
  - Spring Boot JDBC
  - Flink Client API (用于任务提交)
  - Oracle JDBC Driver
- **构建**: Spring Boot Maven Plugin
- **输出**: `monitor-backend-1.0.0-SNAPSHOT.jar`

## 迁移步骤

### 步骤 1: 执行迁移脚本

```bash
# 赋予执行权限
chmod +x migrate-to-multimodule.sh

# 执行迁移
./migrate-to-multimodule.sh
```

脚本会自动完成：
1. 创建 `flink-jobs/` 和 `monitor-backend/` 目录结构
2. 复制源代码到对应模块
3. 复制资源文件到对应模块
4. 备份原始 `pom.xml`
5. 更新所有 Dockerfile 的 JAR 路径
6. 更新构建脚本的 JAR 路径

### 步骤 2: 验证构建

```bash
# 清理并构建所有模块
mvn clean install

# 验证输出
ls -lh flink-jobs/target/flink-jobs-*.jar
ls -lh monitor-backend/target/monitor-backend-*.jar
```

### 步骤 3: 构建 Docker 镜像

```bash
# 使用国内镜像源构建 Flink 镜像
./build-flink-images-cn.sh

# 或使用标准版本
./build-flink-images.sh
```

### 步骤 4: 启动服务

```bash
# 启动所有服务
./start.sh

# 或单独启动
docker-compose up -d
```

## 文件变更清单

### 新增文件

- `pom.xml` (父 POM，替换原文件)
- `flink-jobs/pom.xml`
- `monitor-backend/pom.xml`
- `migrate-to-multimodule.sh`
- `MULTI-MODULE-MIGRATION.md` (本文档)

### 修改文件

- `docker/jobmanager/Dockerfile` - JAR 路径更新
- `docker/jobmanager/Dockerfile.cn` - JAR 路径更新
- `docker/taskmanager/Dockerfile` - JAR 路径更新
- `docker/taskmanager/Dockerfile.cn` - JAR 路径更新
- `monitor/Dockerfile` - JAR 路径更新
- `build-flink-images.sh` - JAR 路径更新
- `build-flink-images-cn.sh` - JAR 路径更新
- `start.sh` - JAR 路径更新（如果有引用）

### 备份文件

- `pom.xml.backup` - 原始 POM 文件
- `*.bak` - Dockerfile 和脚本的备份

## 构建命令对比

### 重构前

```bash
# 构建整个项目
mvn clean package -DskipTests

# 输出
target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar (146MB)
```

### 重构后

```bash
# 构建所有模块
mvn clean install -DskipTests

# 只构建 Flink 任务
mvn clean install -pl flink-jobs -DskipTests

# 只构建 Monitor Backend
mvn clean install -pl monitor-backend -DskipTests

# 输出
flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar (~100MB)
monitor-backend/target/monitor-backend-1.0.0-SNAPSHOT.jar (~50MB)
```

## Docker 构建对比

### 重构前

```dockerfile
# JobManager Dockerfile
COPY target/realtime-data-pipeline-*.jar /opt/flink/usrlib/realtime-data-pipeline.jar

# Monitor Dockerfile
COPY target/realtime-data-pipeline-*.jar /app/app.jar
```

### 重构后

```dockerfile
# JobManager Dockerfile
COPY flink-jobs/target/flink-jobs-*.jar /opt/flink/usrlib/flink-jobs.jar

# Monitor Dockerfile
COPY monitor-backend/target/monitor-backend-*.jar /app/app.jar
```

## 优势

### 1. 清晰的模块边界

- Flink 任务和监控后端完全分离
- 依赖管理更清晰
- 代码职责更明确

### 2. 独立构建和部署

- 可以单独构建 Flink 任务或监控后端
- 减少构建时间（只构建变更的模块）
- 独立的版本管理

### 3. 更小的 JAR 包

- Flink Jobs: ~100MB（只包含 Flink 相关依赖）
- Monitor Backend: ~50MB（只包含 Spring Boot 相关依赖）
- 总大小相同，但模块化更好

### 4. 更好的依赖管理

- 父 POM 统一管理版本
- 避免依赖冲突
- 更容易升级依赖

### 5. 支持独立开发

- 团队可以并行开发不同模块
- 更容易进行单元测试
- 更好的 IDE 支持

## 常见问题

### Q1: 迁移后原来的代码还在吗？

A: 是的，迁移脚本使用 `cp` 复制文件，原始代码保持不变。你可以在验证成功后删除 `src/` 目录。

### Q2: 如何回滚到单模块结构？

A: 使用备份文件恢复：

```bash
# 恢复原始 POM
cp pom.xml.backup pom.xml

# 删除新模块目录
rm -rf flink-jobs/ monitor-backend/

# 恢复 Dockerfile
for f in *.bak; do mv "$f" "${f%.bak}"; done
```

### Q3: 构建失败怎么办？

A: 检查以下几点：

1. 确保所有源代码都已复制到正确的模块
2. 检查 POM 文件中的依赖是否完整
3. 清理 Maven 缓存: `mvn clean`
4. 使用 `-X` 参数查看详细日志: `mvn clean install -X`

### Q4: Docker 镜像构建失败？

A: 确保：

1. Maven 构建成功，JAR 文件存在
2. Dockerfile 中的 JAR 路径正确
3. 使用国内镜像源版本: `./build-flink-images-cn.sh`

### Q5: 如何添加新的模块？

A: 

1. 在父 POM 的 `<modules>` 中添加新模块
2. 创建新模块目录和 `pom.xml`
3. 在新模块的 POM 中指定父 POM
4. 运行 `mvn clean install`

## 最佳实践

### 1. 版本管理

在父 POM 中统一管理版本：

```xml
<properties>
    <flink.version>1.20.0</flink.version>
    <spring.boot.version>2.7.18</spring.boot.version>
</properties>
```

### 2. 依赖管理

使用 `<dependencyManagement>` 统一管理依赖版本，子模块只需声明 groupId 和 artifactId。

### 3. 插件管理

使用 `<pluginManagement>` 统一管理插件配置，子模块继承配置。

### 4. 构建顺序

Maven 会自动处理模块间的依赖关系，按正确顺序构建。

### 5. 持续集成

更新 CI/CD 配置，使用 `mvn clean install` 替代 `mvn clean package`。

## 相关文档

- [Maven 多模块项目](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Spring Boot 多模块项目](https://spring.io/guides/gs/multi-module/)
- [Flink 项目模板](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/configuration/overview/)

## 技术支持

如有问题，请查看：

1. Maven 构建日志: `mvn clean install -X`
2. Docker 构建日志: `docker build --progress=plain`
3. 备份文件: `pom.xml.backup`, `*.bak`

## 总结

多模块项目结构提供了更好的代码组织、依赖管理和构建灵活性。虽然初始设置稍微复杂，但长期来看会带来显著的开发效率提升。
