package com.mikhail.marketdata.web.dto;

import com.mikhail.common.marketdata.Quote;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Котировка в ответе API.
 *
 * @param cached true — внешние источники недоступны, отдана последняя сохранённая котировка
 */
public record QuoteDto(
        String ticker,
        BigDecimal price,
        String currency,
        Instant quotedAt,
        String source,
        boolean cached
) {

    public static QuoteDto live(Quote quote) {
        return new QuoteDto(quote.ticker(), quote.price(), quote.currency(), quote.quotedAt(), quote.source(), false);
    }

    public static QuoteDto cached(Quote quote) {
        return new QuoteDto(quote.ticker(), quote.price(), quote.currency(), quote.quotedAt(), quote.source(), true);
    }
}
