# catalog-service/Dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/catalog-service.jar app.jar
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "app.jar"]