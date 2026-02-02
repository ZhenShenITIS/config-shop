FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
ARG GITHUB_TOKEN
ARG GITHUB_USERNAME
COPY pom.xml .
COPY maven-settings.xml ./maven-settings.xml

RUN mvn -s ./maven-settings.xml \
    -Denv.GITHUB_TOKEN=${GITHUB_TOKEN} \
    -Denv.GITHUB_USERNAME=${GITHUB_USERNAME} \
    dependency:go-offline
COPY src ./src
RUN mvn -s ./maven-settings.xml \
    -Denv.GITHUB_TOKEN=${GITHUB_TOKEN} \
    -Denv.GITHUB_USERNAME=${GITHUB_USERNAME} \
    clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
