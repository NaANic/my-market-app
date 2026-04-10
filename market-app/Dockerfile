# Stage 1: Build — build context must be the project root
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent POM and both module POMs for Docker layer caching
COPY pom.xml .
COPY market-app/pom.xml market-app/
COPY payment-service/pom.xml payment-service/
RUN mvn dependency:go-offline -B -pl market-app -am

# Copy market-app sources and build the executable JAR
COPY market-app/src market-app/src
RUN mvn package -DskipTests -B -pl market-app

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/market-app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
