# ---------- build stage ----------
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# 의존성 레이어 캐싱: 빌드 스크립트를 먼저 복사한다
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스는 마지막에 복사한다 (여기부터 캐시가 깨진다)
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd -r -u 10001 -s /usr/sbin/nologin twinly

# bootJar만 실행하므로 libs 아래에는 실행 가능 JAR 하나만 존재한다
COPY --from=builder /build/build/libs/*.jar app.jar

USER twinly
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
