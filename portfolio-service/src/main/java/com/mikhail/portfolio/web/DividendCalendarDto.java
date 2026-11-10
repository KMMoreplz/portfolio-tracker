package com.mikhail.portfolio.web;

import com.mikhail.common.domain.Currency;

import java.util.List;

/**
 * Дивидендный календарь портфеля: предстоящие выплаты по бумагам из позиций.
 *
 * @param marketDataAvailable false — market-data-service недоступен, список пуст не потому,
 *                            что выплат нет, а потому что данных нет
 */
public record DividendCalendarDto(
        Long portfolioId,
        Currency baseCurrency,
        boolean marketDataAvailable,
        List<DividendCalendarItemDto> items
) {
}
