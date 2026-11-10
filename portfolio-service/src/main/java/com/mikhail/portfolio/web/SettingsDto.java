package com.mikhail.portfolio.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Настройки пользователя в ответе API.
 *
 * @param priceAlertThreshold       порог алертов на изменение цены в процентах, null — не задан
 * @param telegramLinkCode          одноразовый код привязки Telegram-бота, null — код не выдавался или истёк
 * @param telegramLinkCodeExpiresAt срок жизни кода, null — кода нет
 */
public record SettingsDto(
        BigDecimal priceAlertThreshold,
        String telegramLinkCode,
        OffsetDateTime telegramLinkCodeExpiresAt
) {
}
