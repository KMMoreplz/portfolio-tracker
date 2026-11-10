package com.mikhail.telegrambot.notification;

/**
 * Типы событий портфеля, которые portfolio-service передаёт сервису
 * уведомлений по REST. Имена совпадают с JSON-полем eventType.
 */
public enum EventType {
    PORTFOLIO_CREATED,
    PORTFOLIO_DELETED,
    POSITION_CREATED,
    POSITION_UPDATED,
    POSITION_DELETED
}
