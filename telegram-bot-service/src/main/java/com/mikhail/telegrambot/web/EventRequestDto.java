package com.mikhail.telegrambot.web;

import com.mikhail.telegrambot.notification.EventType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Событие портфеля, полученное от portfolio-service по REST.
 * Контракт задокументирован в README; поля позиции заполнены
 * только для событий типа POSITION_*.
 */
public record EventRequestDto(
        @NotNull(message = "тип события обязателен")
        EventType eventType,

        @NotNull(message = "идентификатор портфеля обязателен")
        Long portfolioId,

        Long positionId,
        String ticker,
        BigDecimal quantity,
        BigDecimal averagePrice,
        String currency,
        OffsetDateTime occurredAt
) {
}
