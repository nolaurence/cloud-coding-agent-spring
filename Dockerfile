# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY .mvn ./.mvn
RUN mvn -B -q -s .mvn/settings.xml -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -q -s .mvn/settings.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates libc6 libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system cca && useradd --system --gid cca cca
COPY --from=build /app/target/*.jar /app/app.jar
RUN mkdir -p /app/data /app/workspace && chown -R cca:cca /app
USER cca
EXPOSE 8787
ENV SPRING_DATASOURCE_URL=jdbc:mysql://192.168.100.231:3306/cgnew?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true \
    CCA_DATA_DIR=/app/data \
    CCA_WORKSPACE_ROOT=/app/workspace
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
