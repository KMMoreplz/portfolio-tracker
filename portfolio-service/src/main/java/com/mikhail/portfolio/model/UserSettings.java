package com.mikhail.portfolio.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Настройки пользователя: порог алертов на изменение цены (в процентах) и
 * одноразовый код привязки Telegram-бота со сроком жизни.
 *
 * @param userId                    идентификатор пользователя (внешний ключ на users)
 * @param priceAlertThreshold       порог срабатывания алерта в процентах, null — настройка не задана
 * @param telegramLinkCode          одноразовый код привязки бота, null — код не выдавался
 * @param telegramLinkCodeExpiresAt срок жизни кода, после него код считается не выданным
 */
public record UserSettings(
        Long userId,
        BigDecimal priceAlertThreshold,
        String telegramLinkCode,
        OffsetDateTime telegramLinkCodeExpiresAt
) {
}
