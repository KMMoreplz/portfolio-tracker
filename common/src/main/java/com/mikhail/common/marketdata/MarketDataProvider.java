package com.mikhail.common.marketdata;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Источник рыночных данных: котировки, справки по инструментам, исторические свечи.
 * Реализации находятся в market-data-service (T-Invest API, MOEX ISS и т.д.),
 * потребители в других сервисах видят только этот интерфейс.
 */
public interface MarketDataProvider {

    /** Имя источника для логов, поля {@code source} в котировке и circuit breaker'а. */
    String sourceName();

    /** Последняя цена по тикеру; {@link Optional#empty()}, если тикер не найден у источника. */
    Optional<Quote> findQuote(String ticker);

    /** Справка по инструменту (наименование, ISIN, валюта, лот); пусто, если не найден. */
    Optional<InstrumentInfo> findInstrument(String ticker);

    /** Источник включён (например, у T-Invest не задан токен) — такие источники пропускаются. */
    default boolean isEnabled() {
        return true;
    }

    /** Исторические дневные свечи за период [from, to] включительно. */
    default List<QuoteCandle> findDailyCandles(String ticker, LocalDate from, LocalDate to) {
        return List.of();
    }

    /** Котировки для нескольких тикеров; тикеры без данных просто отсутствуют в результате. */
    default List<Quote> findQuotes(Collection<String> tickers) {
        return tickers.stream()
                .map(this::findQuote)
                .flatMap(Optional::stream)
                .toList();
    }
}
