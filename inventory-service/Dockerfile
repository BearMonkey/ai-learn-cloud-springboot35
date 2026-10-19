# inventory-service/Dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/inventory-service.jar app.jar
EXPOSE 8110
ENTRYPOINT ["java", "-jar", "app.jar"]