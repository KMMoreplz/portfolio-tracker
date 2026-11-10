package com.mikhail.telegrambot.db;

import java.time.OffsetDateTime;

/**
 * Связь Telegram-чата с пользователем: единственное, что бот хранит у себя
 * (вместе с JWT для REST-вызовов от имени пользователя и флагом уведомлений).
 *
 * @param chatId        идентификатор Telegram-чата
 * @param userId        идентификатор пользователя в portfolio-service
 * @param email         email учётной записи (для подтверждения привязки)
 * @param token         JWT пользователя для запросов к portfolio-service
 * @param alertsEnabled включены ли уведомления (/alerts on|off)
 * @param boundAt       когда чат был привязан
 */
public record ChatBinding(
        long chatId,
        Long userId,
        String email,
        String token,
        boolean alertsEnabled,
        OffsetDateTime boundAt
) {
}
