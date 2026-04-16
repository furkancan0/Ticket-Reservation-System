# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install Maven (Alpine package — kept separate from the app layer)
RUN apk add --no-cache maven

WORKDIR /build

# Copy pom.xml first — Docker caches this layer independently.
# Dependency resolution only re-runs when pom.xml changes, not source files.
COPY pom.xml .

# Resolve all dependencies offline (cached layer)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -q

# Copy source and build the fat JAR (tests run separately in CI)
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B -q

# Extract Spring Boot layered JAR for smaller, cache-friendly Docker layers.
# Layers (least → most volatile):
#   dependencies / spring-boot-loader / snapshot-dependencies / application
RUN java -Djarmode=layertools \
         -jar target/ticketing-system-*.jar \
         extract --destination extracted

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user — never run as root in production
RUN addgroup -S ticketing && adduser -S ticketing -G ticketing

WORKDIR /app

# Copy layers in order of change frequency (least → most).
COPY --from=builder --chown=ticketing:ticketing /build/extracted/dependencies/           ./
COPY --from=builder --chown=ticketing:ticketing /build/extracted/spring-boot-loader/     ./
COPY --from=builder --chown=ticketing:ticketing /build/extracted/snapshot-dependencies/  ./
COPY --from=builder --chown=ticketing:ticketing /build/extracted/application/            ./

USER ticketing

EXPOSE 8080

# JVM flags:
#   -XX:+UseContainerSupport     honour cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75.0    use 75 % of container RAM for heap
#   -XX:+UseG1GC                 G1GC — balanced throughput + pause times
#   -Djava.security.egd=...      fast SecureRandom on Linux (avoids /dev/random block)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-local}", \
  "org.springframework.boot.loader.launch.JarLauncher"]

# Docker / Swarm liveness probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
