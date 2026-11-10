package com.mikhail.telegrambot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки Telegram-бота. Токен задаётся только переменной окружения
 * TELEGRAM_BOT_TOKEN и никогда не хранится в репозитории.
 */
@ConfigurationProperties("telegram")
public record TelegramProperties(
        String botToken,
        String apiBaseUrl,
        int pollTimeoutSeconds,
        long pollIntervalMs
) {

    public boolean tokenConfigured() {
        return botToken != null && !botToken.isBlank();
    }
}
