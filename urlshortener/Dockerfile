# Build stage — compile the fat jar once, leave the JDK behind.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependency layer first so source-only changes reuse the Maven cache.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw \
    && ./mvnw -B -q dependency:go-offline -DskipTests

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/urlshortener-*.jar /workspace/app.jar

# Runtime stage — JRE only, smaller attack surface and image size.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# wget is used by the compose healthcheck against /actuator/health.
RUN apk add --no-cache wget \
    && addgroup -S app && adduser -S app -G app

USER app

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080

# No shell form — JVM is PID 1 and receives SIGTERM cleanly on stop.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
