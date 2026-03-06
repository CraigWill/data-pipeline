FROM eclipse-temurin:11-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY target/realtime-data-pipeline-*-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/output/cdc /app/config
EXPOSE 5001
ENTRYPOINT ["java","-Dspring.profiles.active=docker","-cp","/app/app.jar","com.realtime.UnifiedApplication"]
