# Hugging Face Space for DriftGuard. It builds the latest code from GitHub, so the Space
# itself only holds this file and README.md. Secrets (set in the Space settings):
#   GROQ_API_KEY    answers questions (required for answers)
#   GEMINI_API_KEY  fallback answers and embeddings for better search (optional)
FROM eclipse-temurin:21-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates && rm -rf /var/lib/apt/lists/*
# Changes whenever the main branch moves, so a rebuild picks up new commits instead of a cached clone.
ADD https://api.github.com/repos/harishni-5/driftguard/commits/master /tmp/latest-commit.json
RUN git clone --depth 1 https://github.com/harishni-5/driftguard /src
WORKDIR /src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends git python3 ca-certificates libstdc++6 \
 && rm -rf /var/lib/apt/lists/*
COPY --from=node:22-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=build /src/target/driftguard.jar /app/driftguard.jar
RUN id -u 1000 >/dev/null 2>&1 || useradd -m -u 1000 app
USER 1000
ENV PORT=7860 HOME=/tmp
EXPOSE 7860
CMD ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/driftguard.jar", "serve"]
