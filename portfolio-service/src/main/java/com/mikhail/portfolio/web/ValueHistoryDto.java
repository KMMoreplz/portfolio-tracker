package com.mikhail.portfolio.web;

import com.mikhail.common.domain.Currency;

import java.time.LocalDate;
import java.util.List;

/**
 * История стоимости портфеля по дням для графика на дашборде.
 *
 * @param priceSource откуда взяты цены: market-data — истории котировок снаружи,
 *                    local-fallback — сохранённые котировки и средние цены покупки,
 *                    no-data — операций нет, строить нечего
 */
public record ValueHistoryDto(
        Long portfolioId,
        Currency baseCurrency,
        String priceSource,
        List<ValueHistoryPointDto> points
) {
}
