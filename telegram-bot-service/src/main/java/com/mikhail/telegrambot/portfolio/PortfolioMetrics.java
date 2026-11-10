package com.mikhail.telegrambot.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Метрики портфеля из portfolio-service: стоимость, P&L и оценки позиций.
 * Все денежные суммы приведены к базовой валюте портфеля.
 */
public record PortfolioMetrics(
        Long portfolioId,
        String portfolioName,
        String baseCurrency,
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal profit,
        BigDecimal profitPercent,
        BigDecimal dividendsReceived,
        List<PositionValue> positions
) {
}
