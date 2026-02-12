# Dockerfile for CDC Collector
# 需求: 8.3, 8.4 - 提供数据采集组件的Docker镜像，包含所有必要依赖

FROM eclipse-temurin:11-jre-jammy

# 设置工作目录
WORKDIR /opt/cdc-collector

# 安装必要的系统依赖
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# 创建应用用户
RUN groupadd -r cdcuser && useradd -r -g cdcuser cdcuser

# 创建必要的目录
RUN mkdir -p /opt/cdc-collector/lib && \
    mkdir -p /opt/cdc-collector/config && \
    mkdir -p /opt/cdc-collector/logs && \
    mkdir -p /opt/cdc-collector/data && \
    chown -R cdcuser:cdcuser /opt/cdc-collector

# 复制应用程序JAR包
COPY --chown=cdcuser:cdcuser target/realtime-data-pipeline-*.jar /opt/cdc-collector/lib/realtime-data-pipeline.jar

# 复制配置文件模板
COPY --chown=cdcuser:cdcuser docker/cdc-collector/application.yml /opt/cdc-collector/config/application.yml

# 复制Log4j配置
COPY --chown=cdcuser:cdcuser docker/cdc-collector/log4j2.xml /opt/cdc-collector/config/log4j2.xml

# 复制启动脚本
COPY --chown=cdcuser:cdcuser docker/cdc-collector/entrypoint.sh /opt/cdc-collector/entrypoint.sh
RUN chmod +x /opt/cdc-collector/entrypoint.sh

# 切换到应用用户
USER cdcuser

# 暴露健康检查端口
EXPOSE 8080

# 设置环境变量
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
ENV APP_HOME=/opt/cdc-collector
ENV LOG4J_CONFIGURATION_FILE=/opt/cdc-collector/config/log4j2.xml

# 健康检查配置
# 需求: 8.8 - 提供容器健康检查配置
# 使用HTTP健康检查端点验证应用状态
# --interval: 每30秒检查一次
# --timeout: 检查超时时间10秒
# --start-period: 启动后60秒开始检查（满足需求8.5）
# --retries: 连续失败3次后标记为不健康
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${MONITORING_PORT:-8080}/health/live || exit 1

# 启动CDC Collector
# 需求: 8.5, 8.6 - 支持环境变量配置，60秒内完成初始化
ENTRYPOINT ["/opt/cdc-collector/entrypoint.sh"]
