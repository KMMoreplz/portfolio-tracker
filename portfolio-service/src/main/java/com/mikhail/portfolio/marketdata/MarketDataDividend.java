package com.mikhail.portfolio.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Выплата из market-data-service (GET /api/dividends/{ticker}).
 * Поля повторяют JSON внешнего API; отсутствующие данные приходят как null.
 */
public record MarketDataDividend(
        String ticker,
        LocalDate declaredDate,
        LocalDate exDate,
        LocalDate recordDate,
        LocalDate paymentDate,
        BigDecimal amountPerShare,
        String currency,
        String regularity,
        String type,
        String status,
        String source
) {
}
