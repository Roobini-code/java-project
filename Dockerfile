FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /data \
    && chown -R app:app /app /data
COPY --from=build --chown=app:app /workspace/target/taskboard.jar /app/taskboard.jar

ENV DATA_DIR=/data
EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "/app/taskboard.jar"]