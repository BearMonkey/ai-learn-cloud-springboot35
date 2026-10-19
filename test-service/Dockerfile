# test-service/Dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/test-service.jar app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]