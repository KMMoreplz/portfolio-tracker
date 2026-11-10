package com.mikhail.portfolio.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Дневная свеча из market-data-service (GET /api/quotes/{ticker}/history). */
public record MarketDataCandle(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}
