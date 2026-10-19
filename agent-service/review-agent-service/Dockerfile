# review-agent-service/Dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/review-agent-service.jar app.jar
EXPOSE 8140
ENTRYPOINT ["java", "-jar", "app.jar"]