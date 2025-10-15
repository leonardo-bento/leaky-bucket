# syntax=docker/dockerfile:1.6

# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Leverage Gradle wrapper; copy sources
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

# Make wrapper executable and build the application (skip tests for speed)
RUN chmod +x gradlew && ./gradlew clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy built jar
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

# Expose default Spring Boot port (not strictly necessary for SQS listener)
EXPOSE 8080

# Default JVM opts can be overridden at runtime
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
