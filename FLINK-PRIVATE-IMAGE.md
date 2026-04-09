# Flink 私有镜像构建指南

本项目使用自定义的 Flink 镜像，不依赖官方 Docker Hub 的 `flink:*` 镜像，而是从 OpenJDK 17 基础镜像开始构建。

## 架构说明

### 镜像结构

```
openjdk:17-jre-slim (基础镜像)
    ↓
下载 Flink 1.20.0 二进制包
    ↓
添加 Oracle JDBC 驱动
    ↓
复制应用 JAR 包
    ↓
配置 Flink 参数
    ↓
flink-jobmanager:latest / flink-taskmanager:latest
```

### 优势

1. **完全控制**：不依赖外部镜像，所有依赖都在构建时明确指定
2. **安全性**：可以审计所有安装的组件和依赖
3. **定制化**：可以根据需求添加或删除组件
4. **版本锁定**：Flink 版本、JDK 版本、JDBC 驱动版本都明确指定
5. **私有部署**：可以推送到私有镜像仓库，不依赖公网

## 构建方法

### 1. 快速构建（本地使用）

```bash
# 构建最新版本
./build-flink-images.sh

# 或使用 docker-compose 自动构建
docker-compose build jobmanager taskmanager
```

### 2. 构建指定版本

```bash
# 构建并打标签为 v1.0.0
./build-flink-images.sh v1.0.0
```

### 3. 构建并推送到私有仓库

```bash
# 构建并推送到私有仓库
./build-flink-images.sh v1.0.0 registry.example.com/myproject

# 镜像将被标记为：
# - registry.example.com/myproject/flink-jobmanager:v1.0.0
# - registry.example.com/myproject/flink-taskmanager:v1.0.0
```

## 镜像内容

### JobManager 镜像

- **基础镜像**: `openjdk:17-jre-slim`
- **Flink 版本**: 1.20.0 (Scala 2.12)
- **JDBC 驱动**: ojdbc8 19.3.0.0 (兼容 Oracle 11g)
- **应用 JAR**: realtime-data-pipeline-*.jar
- **配置文件**: 
  - `flink-conf.yaml` (JobManager 配置)
  - `log4j.properties` (日志配置)
- **启动脚本**: `custom-entrypoint.sh`

### TaskManager 镜像

- **基础镜像**: `openjdk:17-jre-slim`
- **Flink 版本**: 1.20.0 (Scala 2.12)
- **JDBC 驱动**: ojdbc8 19.3.0.0
- **应用 JAR**: realtime-data-pipeline-*.jar
- **配置文件**: 
  - `flink-conf.yaml` (TaskManager 配置)
  - `log4j.properties` (日志配置)
- **启动脚本**: `custom-entrypoint.sh`

## 目录结构

```
.
├── docker/
│   ├── jobmanager/
│   │   ├── Dockerfile              # JobManager 镜像定义
│   │   ├── entrypoint.sh           # 启动脚本
│   │   ├── flink-conf.yaml         # Flink 配置
│   │   └── log4j.properties        # 日志配置
│   └── taskmanager/
│       ├── Dockerfile              # TaskManager 镜像定义
│       ├── entrypoint.sh           # 启动脚本
│       ├── flink-conf.yaml         # Flink 配置
│       └── log4j.properties        # 日志配置
├── build-flink-images.sh           # 构建脚本
└── docker-compose.yml              # 容器编排配置
```

## 使用方法

### 本地开发

```bash
# 1. 构建镜像
./build-flink-images.sh

# 2. 启动服务
docker-compose up -d

# 3. 查看日志
docker-compose logs -f jobmanager taskmanager

# 4. 访问 Flink Web UI
open http://localhost:8081
```

### 生产部署

```bash
# 1. 构建并推送到私有仓库
./build-flink-images.sh v1.0.0 registry.example.com/myproject

# 2. 在生产服务器上拉取镜像
docker pull registry.example.com/myproject/flink-jobmanager:v1.0.0
docker pull registry.example.com/myproject/flink-taskmanager:v1.0.0

# 3. 更新 docker-compose.yml 使用私有镜像
# 修改 image 字段为私有仓库地址

# 4. 启动服务
docker-compose up -d
```

## 镜像更新

### 更新 Flink 版本

编辑 `docker/jobmanager/Dockerfile` 和 `docker/taskmanager/Dockerfile`：

```dockerfile
ENV FLINK_VERSION=1.21.0 \
    SCALA_VERSION=2.12
```

### 更新 JDK 版本

修改基础镜像：

```dockerfile
FROM openjdk:21-jre-slim
```

### 更新 JDBC 驱动

修改下载 URL：

```dockerfile
RUN curl -L https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/21.1.0.0/ojdbc8-21.1.0.0.jar \
    -o /opt/flink/lib/ojdbc8.jar
```

## 故障排查

### 镜像构建失败

```bash
# 检查 Maven 构建
mvn clean package -DskipTests

# 检查 JAR 文件是否存在
ls -lh target/realtime-data-pipeline-*.jar

# 清理 Docker 缓存重新构建
docker-compose build --no-cache jobmanager taskmanager
```

### 容器启动失败

```bash
# 查看容器日志
docker-compose logs jobmanager
docker-compose logs taskmanager

# 进入容器检查
docker exec -it flink-jobmanager bash
docker exec -it flink-taskmanager-1 bash

# 检查 Flink 安装
ls -lh /opt/flink/
/opt/flink/bin/flink --version
```

### 网络问题

如果下载 Flink 二进制包失败，可以：

1. 使用国内镜像源
2. 手动下载后放到本地，修改 Dockerfile 使用 COPY
3. 使用代理服务器

```dockerfile
# 使用代理
RUN export http_proxy=http://proxy.example.com:8080 && \
    export https_proxy=http://proxy.example.com:8080 && \
    wget -q https://archive.apache.org/dist/flink/...
```

## 性能优化

### 减小镜像大小

1. 使用多阶段构建（如果需要编译）
2. 清理 apt 缓存
3. 删除不必要的文件

### 加速构建

1. 使用 Docker BuildKit
2. 利用构建缓存
3. 并行构建多个镜像

```bash
# 启用 BuildKit
export DOCKER_BUILDKIT=1

# 并行构建
docker-compose build --parallel
```

## 安全建议

1. **定期更新基础镜像**：及时更新 OpenJDK 版本修复安全漏洞
2. **扫描镜像漏洞**：使用 Trivy 或 Clair 扫描镜像
3. **最小权限原则**：容器以非 root 用户运行
4. **签名镜像**：使用 Docker Content Trust 签名镜像
5. **私有仓库**：生产环境使用私有镜像仓库

```bash
# 扫描镜像漏洞
trivy image flink-jobmanager:latest
trivy image flink-taskmanager:latest
```

## 参考资料

- [Apache Flink 官方文档](https://flink.apache.org/docs/stable/)
- [Docker 最佳实践](https://docs.docker.com/develop/dev-best-practices/)
- [OpenJDK 容器镜像](https://hub.docker.com/_/openjdk)
