FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.javadoc.skip=true

FROM eclipse-temurin:21-jre-jammy AS runner
RUN groupadd -r verdikt && useradd -r -g verdikt verdikt
RUN apt-get update && apt-get install -y --no-install-recommends wget curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown verdikt:verdikt app.jar
USER verdikt

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/urandom"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 CMD wget --spider -q http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
