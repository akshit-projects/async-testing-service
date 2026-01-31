# Stage 1: Build
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.11.7_3.7.4 AS build

WORKDIR /app
COPY . .

# Build the application and workers
RUN sbt "root/stage" "workers/stage"

# Stage 2: Run on Alpine
FROM eclipse-temurin:21-alpine-3.23

WORKDIR /app

# Install bash as it is required by scripts
RUN apk add --no-cache bash

# Copy the build artifacts from the build stage
COPY --from=build /app/target/universal/stage ./play
COPY --from=build /app/workers/target/universal/stage ./worker
COPY scripts/entrypoint.sh ./entrypoint.sh

RUN chmod +x ./entrypoint.sh ./play/bin/asynctester ./worker/bin/asynctesterworkers

# Expose the port the app runs on
EXPOSE 9000 8080

# Use the entrypoint script
ENTRYPOINT ["./entrypoint.sh"]
