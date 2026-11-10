package com.mikhail.portfolio.web;

import com.mikhail.common.domain.Currency;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Метрики портфеля: текущая стоимость, вложенные средства, результат в деньгах и процентах,
 * полученные дивиденды и доли позиций. Все суммы приведены к базовой валюте портфеля.
 */
public record MetricsDto(
        Long portfolioId,
        String portfolioName,
        Currency baseCurrency,
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal profit,
        BigDecimal profitPercent,
        BigDecimal dividendsReceived,
        List<PositionMetricsDto> positions,
        OffsetDateTime calculatedAt
) {
}
