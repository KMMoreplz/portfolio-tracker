package com.mikhail.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки публичного API Московской биржи (ISS) — резервный источник котировок.
 */
@ConfigurationProperties(prefix = "moex")
public record MoexProperties(
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutMs
) {
}
