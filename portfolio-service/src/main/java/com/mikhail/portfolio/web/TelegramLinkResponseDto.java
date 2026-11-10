package com.mikhail.portfolio.web;

import java.math.BigDecimal;

/**
 * Ответ на погашение кода привязки: telegram-bot-service получает
 * долгоживущий JWT и данные пользователя для REST-вызовов от его имени.
 *
 * @param userId                идентификатор пользователя
 * @param email                 email учётной записи (subject токена)
 * @param token                 JWT для запросов к portfolio-service
 * @param expiresInSeconds      срок жизни токена в секундах
 * @param priceAlertThreshold   порог алертов из настроек, null — не задан
 */
public record TelegramLinkResponseDto(
        Long userId,
        String email,
        String token,
        long expiresInSeconds,
        BigDecimal priceAlertThreshold
) {
}
