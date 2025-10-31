# Build stage
FROM eclipse-temurin:21 AS builder

# Set working directory
WORKDIR /app

# Copy gradle wrapper and configuration files
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Copy source code
COPY src/ src/

# Make gradlew executable and build the application
RUN chmod +x gradlew && ./gradlew installDist --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

# Copy the built application from builder stage
COPY --from=builder /app/build/install/qbittorrent-exporter /opt/qbittorrent-exporter

ENTRYPOINT ["/opt/qbittorrent-exporter/bin/qbittorrent-exporter"]

EXPOSE 17871
