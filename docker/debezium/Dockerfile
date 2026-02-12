# Debezium Oracle Connector Dockerfile
FROM debezium/connect:2.4

# 切换到 root 用户安装依赖
USER root

# 下载 Oracle JDBC 驱动（需要手动放置）
# 注意：由于许可限制，需要手动下载 ojdbc8.jar 并放在 docker/debezium/ 目录下
COPY ojdbc8.jar /kafka/libs/

# 创建必要的目录
RUN mkdir -p /kafka/config /kafka/data /kafka/logs && \
    chown -R kafka:kafka /kafka

# 切换回 kafka 用户
USER kafka

# 暴露端口
EXPOSE 8083

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8083/ || exit 1
