# 国内镜像源配置指南

## 概述

为了加速 Docker 镜像构建，我们提供了使用国内镜像源的 Dockerfile 版本。

## 国内镜像源列表

### 1. Debian/Ubuntu 软件源

| 镜像站 | 地址 | 速度 |
|--------|------|------|
| 阿里云 | mirrors.aliyun.com | ⭐⭐⭐⭐⭐ |
| 清华大学 | mirrors.tuna.tsinghua.edu.cn | ⭐⭐⭐⭐⭐ |
| 中科大 | mirrors.ustc.edu.cn | ⭐⭐⭐⭐ |
| 网易 | mirrors.163.com | ⭐⭐⭐ |

### 2. Apache Flink 镜像

| 镜像站 | 地址 | 说明 |
|--------|------|------|
| 清华大学 | https://mirrors.tuna.tsinghua.edu.cn/apache/flink/ | 推荐 |
| 阿里云 | https://mirrors.aliyun.com/apache/flink/ | 推荐 |
| 中科大 | https://mirrors.ustc.edu.cn/apache/flink/ | 备用 |

### 3. Maven 中央仓库镜像

| 镜像站 | 地址 | 说明 |
|--------|------|------|
| 阿里云 | https://maven.aliyun.com/repository/public | 推荐 |
| 华为云 | https://repo.huaweicloud.com/repository/maven/ | 备用 |
| 腾讯云 | https://mirrors.cloud.tencent.com/nexus/repository/maven-public/ | 备用 |

### 4. Docker Hub 镜像

| 镜像站 | 地址 | 说明 |
|--------|------|------|
| 阿里云 | https://registry.cn-hangzhou.aliyuncs.com | 需要登录 |
| 中科大 | https://docker.mirrors.ustc.edu.cn | 公开 |
| 网易 | https://hub-mirror.c.163.com | 公开 |

## 使用方法

### 方法 1: 使用国内镜像源 Dockerfile

```bash
# 使用国内镜像源构建
./build-flink-images-cn.sh

# 或者指定版本
./build-flink-images-cn.sh v1.0.0
```

### 方法 2: 修改 start.sh 使用国内镜像

编辑 `start.sh`，在 `build_flink_images()` 函数中：

```bash
# 使用国内镜像源的 Dockerfile
docker build -f docker/jobmanager/Dockerfile.cn -t flink-jobmanager:latest .
docker build -f docker/taskmanager/Dockerfile.cn -t flink-taskmanager:latest .
```

### 方法 3: 配置 Docker Hub 镜像加速

创建或编辑 `/etc/docker/daemon.json`：

```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ]
}
```

重启 Docker：
```bash
sudo systemctl restart docker
```

### 方法 4: 配置 Maven 使用国内镜像

编辑 `~/.m2/settings.xml`：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

## Dockerfile 对比

### 标准版本 (Dockerfile)

```dockerfile
# 使用官方源
RUN apt-get update && \
    apt-get install -y ...

# 从 Apache 官方下载
RUN wget https://archive.apache.org/dist/flink/...

# 从 Maven 中央仓库下载
RUN curl -L https://repo1.maven.org/maven2/...
```

### 国内镜像版本 (Dockerfile.cn)

```dockerfile
# 使用阿里云 Debian 源
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources

# 从清华/阿里云/中科大镜像下载（多个备选）
RUN wget https://mirrors.tuna.tsinghua.edu.cn/apache/flink/... || \
    wget https://mirrors.aliyun.com/apache/flink/... || \
    wget https://mirrors.ustc.edu.cn/apache/flink/...

# 从阿里云 Maven 镜像下载
RUN curl -L https://maven.aliyun.com/repository/public/...
```

## 文件清单

| 文件 | 说明 |
|------|------|
| `docker/jobmanager/Dockerfile` | 标准版 JobManager Dockerfile |
| `docker/jobmanager/Dockerfile.cn` | 国内镜像源版 JobManager Dockerfile |
| `docker/taskmanager/Dockerfile` | 标准版 TaskManager Dockerfile |
| `docker/taskmanager/Dockerfile.cn` | 国内镜像源版 TaskManager Dockerfile |
| `build-flink-images.sh` | 标准版构建脚本 |
| `build-flink-images-cn.sh` | 国内镜像源版构建脚本 |

## 性能对比

### 标准版（国外源）

```
下载 Flink 1.20.0 (约 400MB): ~10-30 分钟
下载 JDBC 驱动 (约 4MB): ~1-3 分钟
apt-get update: ~2-5 分钟
总构建时间: ~15-40 分钟
```

### 国内镜像版

```
下载 Flink 1.20.0 (约 400MB): ~2-5 分钟
下载 JDBC 驱动 (约 4MB): ~10-30 秒
apt-get update: ~30 秒-1 分钟
总构建时间: ~5-10 分钟
```

**速度提升: 约 3-4 倍**

## 故障排查

### 问题 1: 镜像源连接失败

```bash
# 测试镜像源连通性
curl -I https://mirrors.aliyun.com
curl -I https://mirrors.tuna.tsinghua.edu.cn
curl -I https://maven.aliyun.com

# 如果某个源不可用，编辑 Dockerfile.cn 删除该源
```

### 问题 2: Flink 版本不存在

```bash
# 检查镜像源是否有该版本
curl -s https://mirrors.tuna.tsinghua.edu.cn/apache/flink/ | grep 1.20.0

# 如果没有，使用官方源作为备选（已在 Dockerfile.cn 中配置）
```

### 问题 3: Maven 依赖下载失败

```bash
# 清理 Maven 缓存
rm -rf ~/.m2/repository

# 使用阿里云 Maven 镜像重新构建
mvn clean package -DskipTests
```

## 推荐配置

### 国内用户（推荐）

```bash
# 1. 配置 Docker Hub 镜像加速
sudo vim /etc/docker/daemon.json
# 添加镜像源

# 2. 配置 Maven 使用阿里云
vim ~/.m2/settings.xml
# 添加阿里云镜像

# 3. 使用国内镜像源构建
./build-flink-images-cn.sh
```

### 国外用户

```bash
# 使用标准版本
./build-flink-images.sh
```

### 企业内网用户

```bash
# 1. 搭建内部镜像仓库
# 2. 修改 Dockerfile.cn 使用内部镜像源
# 3. 构建并推送到内部仓库
./build-flink-images-cn.sh v1.0.0 registry.company.internal
```

## 自定义镜像源

如果需要使用其他镜像源，编辑 `Dockerfile.cn`：

```dockerfile
# 修改 Debian 源
RUN sed -i 's/deb.debian.org/your-mirror.com/g' /etc/apt/sources.list.d/debian.sources

# 修改 Flink 下载地址
RUN wget https://your-mirror.com/flink/flink-${FLINK_VERSION}/...

# 修改 Maven 仓库
RUN curl -L https://your-maven-mirror.com/...
```

## 安全建议

1. **验证下载文件**: 使用 SHA256 校验和验证下载的文件
2. **使用 HTTPS**: 所有镜像源都使用 HTTPS 协议
3. **定期更新**: 定期更新镜像源列表，删除不可用的源
4. **企业部署**: 建议使用企业内部镜像仓库

## 相关链接

- [阿里云镜像站](https://developer.aliyun.com/mirror/)
- [清华大学开源镜像站](https://mirrors.tuna.tsinghua.edu.cn/)
- [中科大开源镜像站](https://mirrors.ustc.edu.cn/)
- [Apache Flink 官方下载](https://flink.apache.org/downloads.html)
- [Maven 阿里云仓库](https://maven.aliyun.com/mvn/guide)
