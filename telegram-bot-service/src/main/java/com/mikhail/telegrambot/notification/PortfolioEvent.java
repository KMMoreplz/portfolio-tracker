package com.mikhail.telegrambot.notification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Событие портфеля, полученное от portfolio-service. Поля позиции
 * (ticker, quantity, averagePrice, currency) заполнены только для
 * событий типа POSITION_*.
 */
public record PortfolioEvent(
        EventType eventType,
        long portfolioId,
        Long positionId,
        String ticker,
        BigDecimal quantity,
        BigDecimal averagePrice,
        String currency,
        OffsetDateTime occurredAt
) {
}
