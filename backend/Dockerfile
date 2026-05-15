# ========== Build Stage ==========
# Jenkins 에서 빌드한 jar 를 이미지에 넣는 방식이 운영에선 더 효율적이지만,
# 로컬/개발 편의를 위해 멀티스테이지 제공.
# Jenkins 파이프라인에서는 이 Dockerfile 대신 Dockerfile.runtime 으로 교체해서 쓰면 됨.
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN gradle bootJar --no-daemon -x test

# ========== Runtime Stage ==========
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENV TZ=Asia/Seoul
# JVM 컨테이너 인지 + 힙 75% 제한 (Lightsail 16GB 단일 인스턴스에서 다른 컨테이너들과 공존)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
