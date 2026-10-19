# iam-service/Dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/iam-service.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]