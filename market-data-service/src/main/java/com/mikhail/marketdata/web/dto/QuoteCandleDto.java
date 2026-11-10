package com.mikhail.marketdata.web.dto;

import com.mikhail.common.marketdata.QuoteCandle;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Дневная свеча в ответе API. */
public record QuoteCandleDto(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {

    public static QuoteCandleDto from(QuoteCandle candle) {
        return new QuoteCandleDto(
                candle.date(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume());
    }
}
