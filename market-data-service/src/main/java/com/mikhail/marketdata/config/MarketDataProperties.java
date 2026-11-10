package com.mikhail.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Настройки market-data-service: расписание, источники новостей, события, отказоустойчивость.
 */
@ConfigurationProperties(prefix = "market-data")
public record MarketDataProperties(
        List<String> tickers,
        long refreshIntervalMs,
        TradingHours tradingHours,
        Events events,
        News news,
        SmartLab smartLab,
        Resilience resilience
) {

    /**
     * Окно торгов: обновление котировок выполняется только внутри него (включая обогащение кэша).
     */
    public record TradingHours(
            boolean enforced,
            String start,
            String end,
            String zone
    ) {
    }

    /**
     * Публикация событий об изменении цены: REST-вебхук на telegram-bot-service.
     */
    public record Events(
            String webhookUrl,
            double changeThresholdPercent
    ) {
    }

    /**
     * Агрегация новостей из RSS-лент.
     */
    public record News(
            List<String> feeds,
            int limit,
            long cacheTtlSeconds
    ) {
    }

    /**
     * Публичный источник дивидендов smart-lab.ru (fallback после T-Invest API).
     */
    public record SmartLab(
            String baseUrl,
            long connectTimeoutMs,
            long readTimeoutMs
    ) {
    }

    /**
     * Параметры ретраев и circuit breaker'а внешних источников.
     */
    public record Resilience(Retry retry, CircuitBreaker circuitBreaker) {

        public record Retry(int maxAttempts, long waitDurationMs, double multiplier) {
        }

        public record CircuitBreaker(
                int failureRateThresholdPercent,
                int slidingWindowSize,
                int minimumNumberOfCalls,
                long waitDurationInOpenStateMs,
                int permittedCallsInHalfOpenState
        ) {
        }
    }
}
