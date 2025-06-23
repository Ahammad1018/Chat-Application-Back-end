# Use Amazon Corretto (Java JDK)
FROM amazoncorretto:17-alpine AS build

WORKDIR /app

# Copy build tools
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw clean package -DskipTests

# Run stage
FROM amazoncorretto:17-alpine

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
