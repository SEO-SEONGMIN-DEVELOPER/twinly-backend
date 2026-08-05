FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd -r -u 10001 -s /usr/sbin/nologin twinly

COPY --from=builder /build/build/libs/*.jar app.jar

USER twinly
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]