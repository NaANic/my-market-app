# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent POM and module POMs for dependency caching
COPY pom.xml .
COPY payment-service/pom.xml payment-service/
COPY market-app/pom.xml market-app/

# Download dependencies for payment-service module only
RUN mvn dependency:go-offline -B -pl payment-service

# Copy payment-service sources and build
COPY payment-service/src payment-service/src
RUN mvn package -DskipTests -B -pl payment-service

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/payment-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

