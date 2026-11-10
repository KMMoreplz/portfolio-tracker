package com.mikhail.telegrambot.portfolio;

import java.math.BigDecimal;

/**
 * Оценка позиции в метриках портфеля.
 *
 * @param valueInBase  стоимость в базовой валюте портфеля (для сортировки топа)
 * @param profitPercent результат позиции в процентах
 * @param weightPercent доля позиции в портфеле
 */
public record PositionValue(
        String ticker,
        String instrumentName,
        BigDecimal valueInBase,
        String currency,
        BigDecimal profitPercent,
        BigDecimal weightPercent
) {
}
