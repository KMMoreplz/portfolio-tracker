package com.mikhail.common.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Котировка: последняя цена инструмента на момент {@code quotedAt}.
 *
 * @param ticker   тикер инструмента, например SBER
 * @param price    последняя цена
 * @param currency ISO-код валюты цены, например RUB
 * @param quotedAt время котировки (UTC)
 * @param source   источник данных: t-invest, moex-iss, smart-lab, cache
 */
public record Quote(
        String ticker,
        BigDecimal price,
        String currency,
        Instant quotedAt,
        String source
) {
}
