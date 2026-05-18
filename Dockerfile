FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src src

RUN mvn -q verify

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system carechain \
    && useradd --system --gid carechain --create-home --home-dir /home/carechain carechain \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /workspace/target/carechain-1.0.0.jar /app/carechain.jar

RUN chown -R carechain:carechain /app

USER carechain

ENV SPRING_PROFILES_ACTIVE=prod
ENV MANAGEMENT_PORT=8081
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
  CMD curl --fail --silent "http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/carechain.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
