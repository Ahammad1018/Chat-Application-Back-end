# ---------- Build Stage ----------
FROM amazoncorretto:17-alpine AS build

WORKDIR /app

# Copy Maven wrapper and config
COPY mvnw pom.xml ./
COPY .mvn .mvn

# 🔧 Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (without building yet)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application (skip tests for faster build)
RUN ./mvnw clean package -DskipTests

# ---------- Run Stage ----------
FROM amazoncorretto:17-alpine

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose Spring Boot's default port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]
