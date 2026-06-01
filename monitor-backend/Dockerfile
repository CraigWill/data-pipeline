FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY monitor-backend/target/monitor-backend-*-SNAPSHOT.jar /app/app.jar
# 复制 flink-jobs JAR（EmbeddedCdcService 需要上传到 Flink REST API）
COPY flink-jobs/target/flink-jobs-*-SNAPSHOT.jar /opt/flink/usrlib/
RUN mkdir -p /app/output/cdc /app/config
EXPOSE 5001
ENTRYPOINT ["java","-Xms512m","-Xmx2048m","-Dspring.profiles.active=docker","-jar","/app/app.jar"]
