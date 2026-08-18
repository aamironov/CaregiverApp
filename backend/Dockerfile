FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 carebinder \
    && useradd --system --uid 10001 --gid carebinder --home-dir /app carebinder \
    && mkdir -p /data \
    && chown carebinder:carebinder /data
COPY --from=build /workspace/target/carebinder-backend-0.1.0.jar app.jar
ENV PORT=8080
ENV CAREBINDER_DB=/data/carebinder.db
VOLUME ["/data"]
EXPOSE 8080
USER carebinder
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
