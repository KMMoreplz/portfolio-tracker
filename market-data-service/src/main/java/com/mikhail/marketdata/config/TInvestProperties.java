package com.mikhail.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки T-Invest API (Т-Банк). Токен читается из переменной окружения
 * {@code T_INVEST_TOKEN} и никогда не попадает в репозиторий.
 */
@ConfigurationProperties(prefix = "t-invest")
public record TInvestProperties(
        String baseUrl,
        String token,
        long connectTimeoutMs,
        long readTimeoutMs
) {

    /** Провайдер включается только при непустом токене. */
    public boolean enabled() {
        return token != null && !token.isBlank();
    }
}
