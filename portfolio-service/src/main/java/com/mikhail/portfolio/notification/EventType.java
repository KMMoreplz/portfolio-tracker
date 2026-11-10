package com.mikhail.portfolio.notification;

/**
 * Типы событий портфеля для сервиса уведомлений. Имена совпадают с
 * контрактом telegram-bot-service (POST /api/events), см. README.
 */
public enum EventType {
    PORTFOLIO_CREATED,
    PORTFOLIO_DELETED,
    POSITION_CREATED,
    POSITION_UPDATED,
    POSITION_DELETED
}
