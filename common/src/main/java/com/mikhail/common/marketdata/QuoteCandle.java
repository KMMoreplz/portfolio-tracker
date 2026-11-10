package com.mikhail.common.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Дневная свеча.
 *
 * @param date   дата торгов (UTC)
 * @param open   цена открытия
 * @param high   максимум
 * @param low    минимум
 * @param close  цена закрытия
 * @param volume объём сделок в штуках
 */
public record QuoteCandle(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}
