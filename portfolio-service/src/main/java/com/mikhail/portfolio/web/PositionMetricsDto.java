package com.mikhail.portfolio.web;

import com.mikhail.common.domain.Currency;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Оценка одной позиции в метриках портфеля. */
public record PositionMetricsDto(
        String ticker,
        String instrumentName,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        /** Откуда взята currentPrice: источник котировки либо average-price, если котировки нет. */
        String priceSource,
        OffsetDateTime priceAsOf,
        Currency currency,
        BigDecimal value,
        BigDecimal cost,
        BigDecimal profit,
        BigDecimal profitPercent,
        BigDecimal weightPercent,
        BigDecimal valueInBase
) {
}
