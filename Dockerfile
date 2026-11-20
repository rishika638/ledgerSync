# Multi-stage Dockerfile for LedgerSync 2.0 Backend
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper & POM
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy Source & Build
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Production Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
LABEL maintainer="LedgerSync Team <engineering@ledgersync.com>"
LABEL description="AI-Powered Financial Reconciliation & Investigation Platform"

# Install curl for healthchecks
RUN apk add --no-cache curl tzdata && \
    addgroup -S ledgersync && adduser -S ledgersync -G ledgersync

WORKDIR /app

# Copy built JAR from builder
COPY --from=builder /workspace/target/*.jar app.jar

# Set ownership
RUN chown -R ledgersync:ledgersync /app

USER ledgersync

# Port
EXPOSE 8080

# Environment Defaults
ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Container Healthcheck
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
