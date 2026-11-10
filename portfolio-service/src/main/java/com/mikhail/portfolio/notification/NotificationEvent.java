package com.mikhail.portfolio.notification;

import com.mikhail.portfolio.model.Portfolio;
import com.mikhail.portfolio.model.Position;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Событие портфеля для отправки в telegram-bot-service. Фабричные методы
 * собирают событие из доменных объектов; поля позиции заполнены только
 * для событий типа POSITION_*.
 */
public record NotificationEvent(
        EventType eventType,
        long portfolioId,
        Long positionId,
        String ticker,
        BigDecimal quantity,
        BigDecimal averagePrice,
        String currency,
        OffsetDateTime occurredAt
) {

    public static NotificationEvent portfolioCreated(Portfolio portfolio) {
        return new NotificationEvent(EventType.PORTFOLIO_CREATED, portfolio.getId(),
                null, null, null, null, null, OffsetDateTime.now());
    }

    public static NotificationEvent portfolioDeleted(Portfolio portfolio) {
        return new NotificationEvent(EventType.PORTFOLIO_DELETED, portfolio.getId(),
                null, null, null, null, null, OffsetDateTime.now());
    }

    public static NotificationEvent positionCreated(Position position) {
        return fromPosition(EventType.POSITION_CREATED, position);
    }

    public static NotificationEvent positionUpdated(Position position) {
        return fromPosition(EventType.POSITION_UPDATED, position);
    }

    public static NotificationEvent positionDeleted(Position position) {
        return fromPosition(EventType.POSITION_DELETED, position);
    }

    private static NotificationEvent fromPosition(EventType eventType, Position position) {
        return new NotificationEvent(eventType, position.getPortfolioId(), position.getId(),
                position.getTicker(), position.getQuantity(), position.getAveragePrice(),
                position.getCurrency(), OffsetDateTime.now());
    }
}
