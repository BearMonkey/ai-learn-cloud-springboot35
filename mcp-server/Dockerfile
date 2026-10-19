#mcp-server/Dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/mcp-server.jar app.jar
EXPOSE 8130
ENTRYPOINT ["java", "-jar", "app.jar"]