# order-service/Dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/order-service.jar app.jar
EXPOSE 8120
ENTRYPOINT ["java", "-jar", "app.jar"]