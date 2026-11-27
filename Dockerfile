# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper and build files first: this layer stays cached until they change.
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null

# Then the sources.
COPY src/ src/
RUN ./gradlew --no-daemon bootJar -x test

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

# The source label connects the package to the repository in the Container registry:
# without it the workflow's installation token has no right to push the image
# (GitHub Docs, "Working with the Container registry").
LABEL org.opencontainers.image.source="https://github.com/TattletaleStrangler/GomokuBackend"
LABEL org.opencontainers.image.licenses="GPL-3.0-or-later"

# curl is only for the container healthcheck (compose asks /actuator/health).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 1001 app

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# Never run the server as root.
USER app
EXPOSE 8080

ENV TZ=UTC

# JAVA_OPTS lets a host cap the heap (for example -Xmx512m) without rebuilding the
# image; by default the JVM takes 75 % of the container memory limit.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:--XX:MaxRAMPercentage=75} -jar /app/app.jar"]