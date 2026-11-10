package com.mikhail.telegrambot.notification;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Событие об изменении цены, полученное от market-data-service
 * (REST-вебхук PRICE_CHANGED).
 *
 * @param ticker        тикер бумаги
 * @param previousPrice предыдущая котировка
 * @param newPrice      новая котировка
 * @param changePercent изменение в процентах (со знаком)
 * @param currency      ISO-код валюты
 * @param quotedAt      время котировки (UTC)
 * @param source        источник котировки
 */
public record PriceAlertEvent(
        String ticker,
        BigDecimal previousPrice,
        BigDecimal newPrice,
        BigDecimal changePercent,
        String currency,
        Instant quotedAt,
        String source
) {
}
