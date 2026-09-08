# syntax=docker/dockerfile:1.7
FROM gradle:9.1.0-jdk21 AS build
WORKDIR /home/gradle/project

COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :server:buildFatJar --no-daemon -x test \
    --configure-on-demand --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
    -Dkotlin.daemon.jvmargs="-Xmx1024m" \
    -Dkotlin.compiler.execution.strategy=in=process

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /home/gradle/project/server/build/libs/*-all.jar app.jar
EXPOSE 3000
CMD ["java", "-jar", "app.jar"]