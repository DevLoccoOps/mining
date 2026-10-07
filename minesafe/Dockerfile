# --- Build stage: compile and package with Maven + JDK 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first so source changes don't re-download the world
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# --- Runtime stage: slim JRE image ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S minesafe && adduser -S minesafe -G minesafe \
    && mkdir -p /app/target && chown -R minesafe:minesafe /app
USER minesafe

COPY --from=build --chown=minesafe:minesafe /app/target/*.jar app.jar

# HEALTHCHECK mirrors the actuator health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]