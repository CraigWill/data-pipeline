FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY monitor-backend/target/monitor-backend-*-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/output/cdc /app/config
EXPOSE 5001
ENTRYPOINT ["java","-Xms512m","-Xmx2048m","-Dspring.profiles.active=docker","-jar","/app/app.jar"]
