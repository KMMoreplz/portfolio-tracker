package com.mikhail.telegrambot.portfolio;

import java.util.List;

/**
 * Дивидендный календарь портфеля.
 *
 * @param marketDataAvailable false — market-data-service недоступен, список пуст
 *                            не потому, что выплат нет, а потому что данных нет
 */
public record DividendCalendar(
        Long portfolioId,
        String baseCurrency,
        boolean marketDataAvailable,
        List<DividendItem> items
) {
}
