# Build stage
FROM gradle:9.1.0-jdk21 AS build
WORKDIR /home/gradle/project

COPY . .
RUN gradle :server:buildFatJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /home/gradle/project/server/build/libs/*-all.jar app.jar
EXPOSE 3000
CMD ["java", "-jar", "app.jar"]