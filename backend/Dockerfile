# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Run: small JRE image, non-root user, read-only friendly
FROM eclipse-temurin:21-jre
RUN groupadd --system kitepost && useradd --system --gid kitepost --home /app kitepost
WORKDIR /app
COPY --from=build /src/target/kitepost-backend.jar app.jar
USER kitepost
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/urandom"
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
  CMD bash -c "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\r\n\r\n' >&3 && grep -q UP <&3"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
