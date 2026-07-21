# Dockerfile for Flink TaskManager - 私有镜像
# 需求: 8.2, 8.4 - 提供Flink TaskManager的Docker镜像，包含所有必要依赖
# 基于 Eclipse Temurin 17 从头构建，不依赖官方 Flink 镜像

FROM eclipse-temurin:17-jre

# Flink 版本
ENV FLINK_VERSION=1.20.4 \
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
        procps \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# 创建 flink 用户和组
RUN groupadd -r -g ${FLINK_GID} ${FLINK_USER} && \
    useradd -r -u ${FLINK_UID} -g ${FLINK_GID} -m -d /home/${FLINK_USER} -s /bin/bash ${FLINK_USER}

# 安装 Flink：优先使用构建上下文 cache/ 中预下载的 tarball（由 deploy.sh 准备），
# 避免每次构建都从 archive.apache.org 慢速下载；cache/ 缺失时回退在线下载。
COPY cache/ /tmp/flink-cache/
RUN set -ex && \
    TARBALL="flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz" && \
    if [ -f "/tmp/flink-cache/${TARBALL}" ]; then \
        echo "使用 cache/ 中的 Flink tarball: ${TARBALL}" && \
        cp "/tmp/flink-cache/${TARBALL}" "./${TARBALL}"; \
    else \
        echo "cache/ 未找到 ${TARBALL}，回退在线下载" && \
        for i in 1 2 3; do \
            wget -q "https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/${TARBALL}" && break || sleep 10; \
        done; \
    fi && \
    tar -xzf "${TARBALL}" && \
    mv flink-${FLINK_VERSION}/* /opt/flink/ && \
    rm -rf "${TARBALL}" flink-${FLINK_VERSION} /tmp/flink-cache

# 启用 OSS 文件系统插件（checkpoint/savepoint/CSV 直写阿里云 OSS 所需）
# Flink 发行版已在 opt/ 目录内置该插件 jar，只需拷贝到 plugins/ 下即可加载。
# Java 11+ 不再内置 JAXB；flink-oss-fs-hadoop → 阿里云 OSS SDK 需要 javax.xml.bind，
# 必须与插件放同一目录（插件隔离 ClassLoader，lib/ 下的 jar 读不到）。
RUN mkdir -p /opt/flink/plugins/oss-fs-hadoop && \
    cp /opt/flink/opt/flink-oss-fs-hadoop-*.jar /opt/flink/plugins/oss-fs-hadoop/ && \
    set -ex && \
    for i in 1 2 3; do \
        curl -fSL https://repo1.maven.org/maven2/javax/xml/bind/jaxb-api/2.3.1/jaxb-api-2.3.1.jar \
            -o /opt/flink/plugins/oss-fs-hadoop/jaxb-api-2.3.1.jar && \
        curl -fSL https://repo1.maven.org/maven2/com/sun/xml/bind/jaxb-impl/2.3.1/jaxb-impl-2.3.1.jar \
            -o /opt/flink/plugins/oss-fs-hadoop/jaxb-impl-2.3.1.jar && \
        curl -fSL https://repo1.maven.org/maven2/com/sun/xml/bind/jaxb-core/2.3.0.1/jaxb-core-2.3.0.1.jar \
            -o /opt/flink/plugins/oss-fs-hadoop/jaxb-core-2.3.0.1.jar && \
        curl -fSL https://repo1.maven.org/maven2/javax/activation/activation/1.1.1/activation-1.1.1.jar \
            -o /opt/flink/plugins/oss-fs-hadoop/activation-1.1.1.jar && break || sleep 10; \
    done

# 创建必要的目录
RUN mkdir -p /opt/flink/checkpoints && \
    mkdir -p /opt/flink/savepoints && \
    mkdir -p /opt/flink/logs && \
    mkdir -p /opt/flink/data && \
    mkdir -p /opt/flink/usrlib && \
    chown -R ${FLINK_USER}:${FLINK_USER} /opt/flink

# 下载 JDBC 驱动到 Flink lib 目录
# - Oracle ojdbc8：连接 Oracle 业务库（CDC 数据源）
# - OceanBase oceanbase-client：连接 OceanBase（原生协议，推荐）
# - MySQL mysql-connector-j：连接 OceanBase（MySQL 兼容协议）或 MySQL
RUN set -ex && \
    for i in 1 2 3; do \
        curl -L https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/19.3.0.0/ojdbc8-19.3.0.0.jar \
            -o /opt/flink/lib/ojdbc8.jar && break || sleep 10; \
    done && \
    for i in 1 2 3; do \
        curl -L https://repo1.maven.org/maven2/com/oceanbase/oceanbase-client/2.4.18/oceanbase-client-2.4.18.jar \
            -o /opt/flink/lib/oceanbase-client.jar && break || sleep 10; \
    done && \
    for i in 1 2 3; do \
        curl -L https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.5.0/mysql-connector-j-9.5.0.jar \
            -o /opt/flink/lib/mysql-connector-j.jar && break || sleep 10; \
    done && \
    chown ${FLINK_USER}:${FLINK_USER} \
        /opt/flink/lib/ojdbc8.jar \
        /opt/flink/lib/oceanbase-client.jar \
        /opt/flink/lib/mysql-connector-j.jar

# 复制应用程序JAR包（保留原始文件名）
COPY --chown=${FLINK_USER}:${FLINK_USER} flink-jobs/target/flink-jobs-*.jar /opt/flink/usrlib/

# 复制Flink配置文件
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/taskmanager/flink-conf.yaml /opt/flink/conf/flink-conf.yaml

# 复制Log4j配置
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/taskmanager/log4j.properties /opt/flink/conf/log4j.properties

# 复制自定义entrypoint脚本
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/taskmanager/entrypoint.sh /opt/flink/bin/custom-entrypoint.sh
RUN chmod +x /opt/flink/bin/custom-entrypoint.sh

# 切换到 flink 用户
USER ${FLINK_USER}

# 暴露TaskManager端口
# 6121: Data交换端口
# 6122: RPC通信端口
# 8080: Metrics端口
# 9249: Prometheus metrics端口
EXPOSE 6121 6122 8080 9249

# 设置环境变量
ENV PATH=$FLINK_HOME/bin:$PATH

# 健康检查配置
# TaskManager通过检查进程是否存在来判断健康状态
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD pgrep -f "org.apache.flink.runtime.taskexecutor.TaskManagerRunner" || exit 1

# 启动TaskManager
ENTRYPOINT ["/opt/flink/bin/custom-entrypoint.sh"]
