# Dockerfile for Flink JobManager - 私有镜像
# 需求: 8.1, 8.4 - 提供Flink JobManager的Docker镜像，包含所有必要依赖
# 基于 Eclipse Temurin 17 从头构建，不依赖官方 Flink 镜像

FROM eclipse-temurin:17-jre

# Flink 版本
ENV FLINK_VERSION=1.20.0 \
    SCALA_VERSION=2.12 \
    FLINK_HOME=/opt/flink \
    FLINK_USER=flink \
    FLINK_UID=9999 \
    FLINK_GID=9999 \
    DEBIAN_FRONTEND=noninteractive

# 设置工作目录
WORKDIR /opt/flink

# 安装必要的系统依赖（添加重试和错误处理）
RUN set -ex && \
    for i in 1 2 3; do \
        apt-get update && break || sleep 5; \
    done && \
    apt-get install -y --no-install-recommends \
        bash \
        curl \
        wget \
        netcat-openbsd \
        ca-certificates \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# 创建 flink 用户和组
RUN groupadd -r -g ${FLINK_GID} ${FLINK_USER} && \
    useradd -r -u ${FLINK_UID} -g ${FLINK_GID} -m -d /home/${FLINK_USER} -s /bin/bash ${FLINK_USER}

# 下载并安装 Flink（添加重试机制）
RUN set -ex && \
    for i in 1 2 3; do \
        wget -q https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz && break || sleep 10; \
    done && \
    tar -xzf flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz && \
    mv flink-${FLINK_VERSION}/* /opt/flink/ && \
    rm -rf flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz flink-${FLINK_VERSION}

# 创建必要的目录
RUN mkdir -p /opt/flink/checkpoints && \
    mkdir -p /opt/flink/savepoints && \
    mkdir -p /opt/flink/ha && \
    mkdir -p /opt/flink/logs && \
    mkdir -p /opt/flink/usrlib && \
    chown -R ${FLINK_USER}:${FLINK_USER} /opt/flink

# 下载 Oracle JDBC 驱动到 Flink lib 目录（添加重试机制）
RUN set -ex && \
    for i in 1 2 3; do \
        curl -L https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/19.3.0.0/ojdbc8-19.3.0.0.jar \
            -o /opt/flink/lib/ojdbc8.jar && break || sleep 10; \
    done && \
    chown ${FLINK_USER}:${FLINK_USER} /opt/flink/lib/ojdbc8.jar

# 复制应用程序JAR包（保留原始文件名，EmbeddedCdcService 依赖此路径）
COPY --chown=${FLINK_USER}:${FLINK_USER} flink-jobs/target/flink-jobs-*.jar /opt/flink/usrlib/

# 复制Flink配置文件
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/jobmanager/flink-conf.yaml /opt/flink/conf/flink-conf.yaml

# 复制Log4j配置
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/jobmanager/log4j.properties /opt/flink/conf/log4j.properties

# 复制自定义entrypoint脚本
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/jobmanager/entrypoint.sh /opt/flink/bin/custom-entrypoint.sh
RUN chmod +x /opt/flink/bin/custom-entrypoint.sh

# 切换到 flink 用户
USER ${FLINK_USER}

# 暴露JobManager端口
# 6123: RPC通信端口
# 8081: Web UI端口
# 6124: Blob服务器端口
# 9249: Prometheus metrics端口
EXPOSE 6123 8081 6124 9249

# 设置环境变量
ENV PATH=$FLINK_HOME/bin:$PATH

# 健康检查配置
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/overview || exit 1

# 启动JobManager
ENTRYPOINT ["/opt/flink/bin/custom-entrypoint.sh"]
