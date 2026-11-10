package com.mikhail.telegrambot.portfolio;

import java.math.BigDecimal;

/**
 * Результат погашения кода привязки: portfolio-service подтвердил код
 * и выдал долгоживущий JWT для REST-вызовов от имени пользователя.
 *
 * @param userId                идентификатор пользователя
 * @param email                 email учётной записи
 * @param token                 JWT для запросов к portfolio-service
 * @param expiresInSeconds      срок жизни токена в секундах
 * @param priceAlertThreshold   порог алертов из настроек, null — не задан
 */
public record LinkResult(
        Long userId,
        String email,
        String token,
        long expiresInSeconds,
        BigDecimal priceAlertThreshold
) {
}
