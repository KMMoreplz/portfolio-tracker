package com.mikhail.telegrambot.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Событие об изменении цены от market-data-service (REST-вебхук PRICE_CHANGED).
 * Контракт совпадает с payload'ом WebhookPriceEventPublisher.
 */
public record PriceEventRequestDto(
        String event,

        @NotBlank(message = "тикер обязателен")
        String ticker,

        BigDecimal previousPrice,

        @NotNull(message = "новая цена обязательна")
        BigDecimal newPrice,

        @NotNull(message = "изменение в процентах обязательно")
        BigDecimal changePercent,

        String currency,

        Instant quotedAt,

        String source
) {
}
