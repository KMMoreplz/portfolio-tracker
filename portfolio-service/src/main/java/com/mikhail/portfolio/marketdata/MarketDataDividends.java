package com.mikhail.portfolio.marketdata;

import java.util.List;

/** Ответ market-data-service: предстоящие и прошедшие выплаты по тикеру. */
public record MarketDataDividends(
        String ticker,
        String source,
        List<MarketDataDividend> upcoming,
        List<MarketDataDividend> past
) {
}
