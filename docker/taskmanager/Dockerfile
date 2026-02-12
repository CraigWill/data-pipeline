# Dockerfile for Flink TaskManager
# 需求: 8.2, 8.4 - 提供Flink TaskManager的Docker镜像，包含所有必要依赖

FROM flink:1.18.0-java11

# 设置工作目录
WORKDIR /opt/flink

# 安装必要的系统依赖
USER root
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# 创建必要的目录
RUN mkdir -p /opt/flink/checkpoints && \
    mkdir -p /opt/flink/savepoints && \
    mkdir -p /opt/flink/logs && \
    mkdir -p /opt/flink/lib && \
    mkdir -p /opt/flink/data && \
    chown -R flink:flink /opt/flink

# 复制自定义entrypoint脚本
COPY --chown=flink:flink docker/taskmanager/entrypoint.sh /opt/flink/bin/custom-entrypoint.sh
RUN chmod +x /opt/flink/bin/custom-entrypoint.sh

# 切换回flink用户
USER flink

# 复制应用程序JAR包
COPY --chown=flink:flink target/realtime-data-pipeline-*.jar /opt/flink/usrlib/realtime-data-pipeline.jar

# 备份原始Flink配置文件
RUN cp /opt/flink/conf/flink-conf.yaml /opt/flink/conf/flink-conf.yaml.original || true

# 复制Flink配置文件（如果存在）
COPY --chown=flink:flink docker/taskmanager/flink-conf.yaml /opt/flink/conf/flink-conf.yaml

# 复制Log4j配置
COPY --chown=flink:flink docker/taskmanager/log4j.properties /opt/flink/conf/log4j.properties

# 暴露TaskManager端口
# 6121: Data交换端口
# 6122: RPC通信端口
# 8080: Metrics端口
# 9249: Prometheus metrics端口
EXPOSE 6121 6122 8080 9249

# 设置环境变量
ENV FLINK_HOME=/opt/flink
ENV PATH=$FLINK_HOME/bin:$PATH

# 健康检查配置
# 需求: 8.8 - 提供容器健康检查配置
# TaskManager通过检查进程是否存在来判断健康状态
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD pgrep -f "org.apache.flink.runtime.taskexecutor.TaskManagerRunner" || exit 1

# 启动TaskManager
# 使用自定义entrypoint脚本支持环境变量配置（需求 8.6）
ENTRYPOINT ["/opt/flink/bin/custom-entrypoint.sh"]
