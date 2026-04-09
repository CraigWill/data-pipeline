# Flink 私有镜像 - 优化下载方式

## 变更说明

将 Flink 私有镜像的构建方式从使用 `wget` 改为使用 `curl`，并优化下载逻辑。

## 变更原因

1. **减少依赖**：`curl` 通常已包含在基础镜像中，不需要额外安装 `wget`
2. **更好的错误处理**：`curl` 的 `-f` 选项可以在 HTTP 错误时返回非零退出码
3. **统一工具**：项目中其他地方（如下载 JDBC 驱动）已经使用 `curl`
4. **更清晰的逻辑**：使用变量存储文件名，代码更易读

## 关于 Maven 仓库的说明

经过调研，Flink 的二进制发行版（bin.tgz）并不发布到 Maven Central 仓库。Maven 仓库中只包含 Flink 的各个模块 JAR 文件，不包含完整的二进制发行版。

因此，我们仍然需要从 Apache 镜像站下载 Flink 二进制发行版，但可以优化下载方式。

## 技术实现

### 旧方式（使用 wget）

```dockerfile
RUN set -ex && \
    wget -q https://mirrors.tuna.tsinghua.edu.cn/apache/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz || \
    wget -q https://mirrors.aliyun.com/apache/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz || \
    wget -q https://mirrors.ustc.edu.cn/apache/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz || \
    wget -q https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz && \
    tar -xzf flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz && \
    mv flink-${FLINK_VERSION}/* /opt/flink/ && \
    rm -rf flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz flink-${FLINK_VERSION}
```

**问题**：
- 需要安装 `wget` 工具
- 文件名重复多次，容易出错
- 下载到当前目录，可能污染工作目录

### 新方式（使用 curl）

```dockerfile
RUN set -ex && \
    FLINK_TAR="flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz" && \
    curl -fSL "https://mirrors.tuna.tsinghua.edu.cn/apache/flink/flink-${FLINK_VERSION}/${FLINK_TAR}" -o /tmp/flink.tgz || \
    curl -fSL "https://mirrors.aliyun.com/apache/flink/flink-${FLINK_VERSION}/${FLINK_TAR}" -o /tmp/flink.tgz || \
    curl -fSL "https://mirrors.ustc.edu.cn/apache/flink/flink-${FLINK_VERSION}/${FLINK_TAR}" -o /tmp/flink.tgz || \
    curl -fSL "https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/${FLINK_TAR}" -o /tmp/flink.tgz && \
    tar -xzf /tmp/flink.tgz -C /tmp && \
    mv /tmp/flink-${FLINK_VERSION}/* /opt/flink/ && \
    rm -rf /tmp/flink.tgz /tmp/flink-${FLINK_VERSION}
```

**优势**：
- 不需要安装额外工具（`curl` 已包含在基础镜像中）
- 使用变量存储文件名，代码更清晰
- 下载到 `/tmp` 目录，保持工作目录整洁
- `curl -fSL` 选项：
  - `-f`: HTTP 错误时返回非零退出码
  - `-S`: 显示错误信息
  - `-L`: 跟随重定向

## 修改的文件

1. `docker/jobmanager/Dockerfile.cn` - JobManager 镜像 Dockerfile
2. `docker/taskmanager/Dockerfile.cn` - TaskManager 镜像 Dockerfile
3. `build-flink-images-cn.sh` - 构建脚本（更新说明文字）

## 下载源优先级

1. **清华大学镜像站**（首选）
   - URL: https://mirrors.tuna.tsinghua.edu.cn/apache/flink/
   - 特点: 国内访问速度快，更新及时

2. **阿里云镜像站**（备选）
   - URL: https://mirrors.aliyun.com/apache/flink/
   - 特点: 稳定性高，CDN 覆盖广

3. **中国科学技术大学镜像站**（备选）
   - URL: https://mirrors.ustc.edu.cn/apache/flink/
   - 特点: 教育网访问快

4. **Apache 官方归档**（最后备选）
   - URL: https://archive.apache.org/dist/flink/
   - 特点: 最权威，但国内访问较慢

## 使用方法

### 1. 构建镜像

```bash
# 使用默认标签 (latest)
./build-flink-images-cn.sh

# 使用自定义标签
./build-flink-images-cn.sh v1.0.0

# 推送到私有仓库
./build-flink-images-cn.sh v1.0.0 registry.example.com/myproject
```

### 2. 验证构建

```bash
# 查看镜像
docker images | grep flink

# 测试 JobManager 镜像
docker run --rm flink-jobmanager:latest flink --version

# 测试 TaskManager 镜像
docker run --rm flink-taskmanager:latest flink --version
```

## 总结

使用 `curl` 替代 `wget` 的优势：

1. ✅ 减少镜像依赖（不需要安装 wget）
2. ✅ 更好的错误处理（-f 选项）
3. ✅ 统一工具使用（项目中其他地方也用 curl）
4. ✅ 代码更清晰（使用变量存储文件名）
5. ✅ 保持工作目录整洁（下载到 /tmp）
