# Multi-stage build를 사용하여 이미지 크기 최적화
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Gradle 캐시 활용을 위해 의존성 먼저 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle build -x test --no-daemon || true

# 소스 코드 복사 및 빌드
COPY . .
RUN gradle clean build -x test --no-daemon

# 실행 단계
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]

