# syntax=docker/dockerfile:1
# ONE image: the Spring Boot jar contains the compiled React app. Build context = repo root.
#   docker build -t materialbook .

# 1) Build the SPA.
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Build the jar with the SPA inside (src/main/resources/static -> classpath:/static/). No dependency:go-offline (it hangs).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# 3) Runtime.
FROM eclipse-temurin:21-jre
# Fixed uid/gid 10001 so a bind-mounted host folder can be chown'ed to it (see docs/DEPLOYMENT.md).
# Uploads live in /app/uploads (= the default ./uploads relative to WORKDIR); docker-compose.yml bind-mounts ./uploads there.
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
RUN mkdir /app/uploads && chown 10001:10001 /app/uploads && chmod 700 /app/uploads
COPY --from=build /app/target/*.jar app.jar
COPY docker/healthcheck.sh /app/healthcheck.sh
USER app
# 8080 = plain HTTP (default). With SERVER_PORT=8443 SERVER_SSL_ENABLED=true ... the same port serves HTTPS (see docs/DEPLOYMENT.md).
EXPOSE 8080
# JVM flags come from JAVA_TOOL_OPTIONS. /tmp must be writable (a tmpfs when the root fs is read-only).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD ["sh", "/app/healthcheck.sh"]
ENTRYPOINT ["java", "-jar", "app.jar"]
