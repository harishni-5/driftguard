# DriftGuard website. Build: docker build -t driftguard .   Run: docker run -p 7860:7860 -e GROQ_API_KEY driftguard
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
# git clones repos; python3 and node run the parser helpers. Nothing from an audited repo is ever executed.
RUN apt-get update \
 && apt-get install -y --no-install-recommends git python3 ca-certificates libstdc++6 \
 && rm -rf /var/lib/apt/lists/*
COPY --from=node:22-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=build /src/target/driftguard.jar /app/driftguard.jar
# Run as an unprivileged user (uid 1000 is what Hugging Face Spaces expects).
RUN id -u 1000 >/dev/null 2>&1 || useradd -m -u 1000 app
USER 1000
# Defaults suit a small free instance (512 MB). On a bigger machine raise MaxRAMPercentage or drop JAVA_OPTS.
ENV PORT=7860 HOME=/tmp \
    JAVA_OPTS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
EXPOSE 7860
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/driftguard.jar serve"]
