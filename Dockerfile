# Runtime-only Dockerfile
# Gradle 빌드는 CI/CD에서 네이티브(amd64)로 수행하고, 여기서는 JAR만 패키징
# ARM64(Graviton) EC2에서 실행되므로 buildx --platform linux/arm64로 빌드됨
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# CI/CD에서 빌드된 JAR 복사 (워크플로우에서 프로젝트 루트에 복사해둠)
COPY app.jar app.jar

# 보안: root 대신 전용 비권한 유저로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN chown appuser:appgroup /app/app.jar
USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"

# 헬스체크: 30초마다 /actuator/health 확인, 3회 연속 실패 시 unhealthy
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
