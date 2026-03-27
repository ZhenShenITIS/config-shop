# syntax=docker/dockerfile:1
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml maven-settings.xml ./

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=github_token,env=GITHUB_TOKEN \
    --mount=type=secret,id=github_user,env=GITHUB_USERNAME \
    mvn -s ./maven-settings.xml dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=github_token,env=GITHUB_TOKEN \
    --mount=type=secret,id=github_user,env=GITHUB_USERNAME \
    mvn -s ./maven-settings.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S botgroup && adduser -S botuser -G botgroup
USER botuser

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
