# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /home/gradle/project
# Keep the wrapper distribution and dependency cache on the cache mount below
ENV GRADLE_USER_HOME=/home/gradle/.gradle

COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew :server:buildFatJar --no-daemon -x test \
    --configure-on-demand --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
    -Dkotlin.daemon.jvmargs="-Xmx1024m" \
    -Dkotlin.compiler.execution.strategy=in-process

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /home/gradle/project/server/build/libs/*-all.jar app.jar
COPY server/src/main/resources/logback.xml /app/logback.xml
EXPOSE 3000
CMD ["java", "-Dlogback.configurationFile=/app/logback.xml", "-jar", "app.jar"]