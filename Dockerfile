# ─────────────────────────────────────────────────────────────
# Stage 1: BUILD — Maven compiles source + runs tests + packages JAR
# ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml first — Docker layer cache avoids re-downloading
# dependencies on every code change
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code and build
COPY src ./src
RUN mvn clean package -q

# ─────────────────────────────────────────────────────────────
# Stage 2: RUN — Minimal JRE image (no Maven, no source code)
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/browser-history-manager.jar app.jar

# The app is a Swing GUI — in a CI/CD context we expose a health
# endpoint port. For local deployment, set DISPLAY env variable.
EXPOSE 8080

# Entry point
ENTRYPOINT ["java", "-jar", "app.jar"]
