# ----------- Build Stage -----------
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

# IMPORTANT: copy only the project folder, not the entire repo
COPY DistributedJobQueue/ .

RUN gradle clean bootJar --no-daemon

# ----------- Runtime Stage -----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
