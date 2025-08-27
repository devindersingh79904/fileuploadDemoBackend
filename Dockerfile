# ---- Stage 1: Build the application ----
FROM maven:3.9.4-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy project files into container
COPY . .

# Build the jar (skip tests for faster CI/CD builds)
RUN mvn clean package -DskipTests


# ---- Stage 2: Run the application ----
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy jar file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port (Render sets $PORT, Spring reads server.port=${PORT})
EXPOSE 8080

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
