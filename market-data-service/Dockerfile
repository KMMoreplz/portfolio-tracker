# Сборка в контейнере: монорепо Maven (common + market-data-service), затем минимальный JRE.
# Контекст сборки — корень репозитория (см. docker-compose.yml).
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY . .
# Общий слой для всех сервисов: зависимости Maven скачиваются один раз и кэшируются.
RUN mvn -q -B -pl portfolio-service,market-data-service,telegram-bot-service -am \
    dependency:go-offline -Dmaven.test.skip=true || true
RUN mvn -q -B -pl market-data-service -am package -Dmaven.test.skip=true \
    && cp market-data-service/target/market-data-service-*.jar /build/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
