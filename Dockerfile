# Stage 1: Build
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.11.7_3.7.4 AS build

WORKDIR /app
COPY . .

# Build the application
RUN sbt stage

# Stage 2: Run on Alpine
FROM eclipse-temurin:21-alpine-3.23

WORKDIR /app

# Install bash as it is required by Play Framework startup scripts
RUN apk add --no-cache bash

# Copy the build artifacts from the build stage
COPY --from=build /app/target/universal/stage .

# Expose the port the app runs on
EXPOSE 9000

# Run the application
ENTRYPOINT ["./bin/asynctester", "-Dplay.http.secret.key=changeme"]
