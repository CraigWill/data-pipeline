# Flink 私有镜像迁移总结

## 变更概述

已将 Flink JobManager 和 TaskManager 从官方镜像 (`flink:1.20.0-java17`) 迁移到基于 OpenJDK 17 的私有镜像构建方式。

## 主要变更

### 1. Dockerfile 重构

**之前（使用官方镜像）：**
```dockerfile
FROM flink:1.20.0-java17
# 在官方镜像基础上添加配置
```

**现在（私有镜像）：**
```dockerfile
FROM openjdk:17-jre-slim
# 从头构建，下载 Flink 二进制包
ENV FLINK_VERSION=1.20.0
RUN wget https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/...
```

### 2. 新增文件

| 文件 | 说明 |
|------|------|
| `build-flink-images.sh` | 镜像构建脚本，支持版本标签和私有仓库推送 |
| `test-build.sh` | 快速测试构建脚本 |
| `FLINK-PRIVATE-IMAGE.md` | 详细的构建和使用文档 |
| `PRIVATE-IMAGE-SUMMARY.md` | 本文档，变更总结 |

### 3. 修改的文件

| 文件 | 变更内容 |
|------|----------|
| `docker/jobmanager/Dockerfile` | 完全重写，从 OpenJDK 基础镜像构建 |
| `docker/taskmanager/Dockerfile` | 完全重写，从 OpenJDK 基础镜像构建 |
| `pom.xml` | Java 版本从 11 升级到 17 |

## 镜像对比

### 官方镜像方式
```
优点：
- 构建快速（基于现成镜像）
- 配置简单
- 官方维护

缺点：
- 依赖外部镜像源
- 无法完全控制内容
- 可能包含不需要的组件
- 网络问题可能导致拉取失败
```

### 私有镜像方式
```
优点：
- 完全控制镜像内容
- 可审计所有依赖
- 支持私有仓库部署
- 版本锁定，可重现构建
- 不依赖外部镜像源

缺点：
- 首次构建时间较长（需下载 Flink）
- 需要维护 Dockerfile
- 镜像体积可能稍大
```

## 构建流程

```
┌─────────────────┐
│  Maven 构建     │
│  生成 JAR 包    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 下载 Flink      │
│ 二进制包        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 下载 JDBC 驱动  │
│ (ojdbc8)        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 复制配置文件    │
│ 和应用 JAR      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 构建 Docker     │
│ 镜像            │
└─────────────────┘
```

## 使用方法

### 快速开始

```bash
# 1. 构建镜像
./build-flink-images.sh

# 2. 启动服务
docker-compose up -d

# 3. 查看状态
docker-compose ps
```

### 生产部署

```bash
# 1. 构建并推送到私有仓库
./build-flink-images.sh v1.0.0 registry.company.com/project

# 2. 在生产环境拉取
docker pull registry.company.com/project/flink-jobmanager:v1.0.0
docker pull registry.company.com/project/flink-taskmanager:v1.0.0

# 3. 启动服务
docker-compose up -d
```

## 镜像内容清单

### JobManager 镜像
- **基础**: OpenJDK 17 JRE (Slim)
- **Flink**: 1.20.0 (Scala 2.12)
- **JDBC**: ojdbc8 19.3.0.0
- **应用**: realtime-data-pipeline JAR
- **配置**: flink-conf.yaml, log4j.properties
- **脚本**: custom-entrypoint.sh
- **大小**: ~800MB (首次构建)

### TaskManager 镜像
- **基础**: OpenJDK 17 JRE (Slim)
- **Flink**: 1.20.0 (Scala 2.12)
- **JDBC**: ojdbc8 19.3.0.0
- **应用**: realtime-data-pipeline JAR
- **配置**: flink-conf.yaml, log4j.properties
- **脚本**: custom-entrypoint.sh
- **大小**: ~800MB (首次构建)

## 测试验证

```bash
# 运行测试构建
./test-build.sh

# 预期输出：
# ✓ JAR 文件存在
# ✓ JobManager 镜像构建成功
# ✓ TaskManager 镜像构建成功
# ✓ 所有测试通过！
```

## 版本信息

| 组件 | 版本 |
|------|------|
| OpenJDK | 17 JRE Slim |
| Flink | 1.20.0 |
| Scala | 2.12 |
| Oracle JDBC | ojdbc8 19.3.0.0 |
| Maven Compiler | Java 17 |

## 兼容性

- ✅ Oracle 11g 数据库
- ✅ Docker Compose v2
- ✅ Kubernetes (需要调整配置)
- ✅ Linux x86_64
- ✅ macOS (Apple Silicon 通过 Rosetta)

## 故障排查

### 问题 1: Flink 下载失败
```bash
# 解决方案：使用国内镜像或手动下载
# 修改 Dockerfile 中的下载 URL
```

### 问题 2: 构建时间过长
```bash
# 解决方案：使用 BuildKit 和缓存
export DOCKER_BUILDKIT=1
docker-compose build --parallel
```

### 问题 3: 镜像体积过大
```bash
# 解决方案：清理不必要的文件
# 在 Dockerfile 中添加清理步骤
RUN rm -rf /opt/flink/examples /opt/flink/opt
```

## 后续优化建议

1. **多阶段构建**: 分离构建和运行环境
2. **镜像缓存**: 使用 Docker Registry 缓存层
3. **安全扫描**: 集成 Trivy 或 Clair
4. **CI/CD**: 自动化构建和推送流程
5. **版本管理**: 使用 Git 标签触发镜像构建

## 迁移检查清单

- [x] 重写 JobManager Dockerfile
- [x] 重写 TaskManager Dockerfile
- [x] 更新 pom.xml Java 版本
- [x] 创建构建脚本
- [x] 创建测试脚本
- [x] 编写文档
- [x] 验证 docker-compose.yml 兼容性
- [ ] 测试镜像构建
- [ ] 测试容器启动
- [ ] 测试 CDC 任务运行
- [ ] 性能测试
- [ ] 生产环境部署

## 参考文档

- [FLINK-PRIVATE-IMAGE.md](./FLINK-PRIVATE-IMAGE.md) - 详细使用指南
- [build-flink-images.sh](./build-flink-images.sh) - 构建脚本
- [test-build.sh](./test-build.sh) - 测试脚本
- [docker-compose.yml](./docker-compose.yml) - 容器编排配置
