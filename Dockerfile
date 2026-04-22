# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
