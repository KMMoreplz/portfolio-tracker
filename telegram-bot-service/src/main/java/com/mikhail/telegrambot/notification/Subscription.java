package com.mikhail.telegrambot.notification;

/**
 * Подписка чата на события портфеля: пользователь выбирает, о каком
 * портфеле он хочет получать уведомления.
 */
public record Subscription(long chatId, long portfolioId) {
}
