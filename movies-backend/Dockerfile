# syntax=docker/dockerfile:1

# ---- Stage 1: build with the full JDK ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first: this layer is rebuilt only when pom.xml changes, not on every code change
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --quiet dependency:go-offline

COPY src/ src/
# Tests run in CI (they need Docker for Testcontainers); the image build only packages
RUN ./mvnw --batch-mode --quiet package -DskipTests \
 && cp target/movies-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Stage 2: run with only the JRE (smaller image, no compiler for an attacker to use) ----
FROM eclipse-temurin:25-jre
# Never run as root inside the container
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app

# One image layer per Spring Boot layer, from least to most frequently changed,
# so a code change only re-ships the small "application" layer
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080
# The JVM sizes its heap from the container's memory limit; leave 25% for everything else
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
