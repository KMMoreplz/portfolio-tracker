package com.mikhail.marketdata.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Событие об изменении цены: публикуется после сохранения новой котировки,
 * если изменение к прошлой котировке превысило порог.
 *
 * @param ticker         тикер
 * @param previousPrice  предыдущая сохранённая цена
 * @param newPrice       новая цена
 * @param changePercent  изменение в процентах (со знаком)
 * @param currency       ISO-код валюты
 * @param quotedAt       время котировки (UTC)
 * @param source         источник котировки
 */
public record PriceChangedEvent(
        String ticker,
        BigDecimal previousPrice,
        BigDecimal newPrice,
        BigDecimal changePercent,
        String currency,
        Instant quotedAt,
        String source
) {
    public static final String EVENT_TYPE = "PRICE_CHANGED";
}
