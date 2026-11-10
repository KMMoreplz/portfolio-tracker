package com.mikhail.marketdata.web.dto;

import java.util.List;

/** Ответ {@code GET /api/dividends/{ticker}}: предстоящие и прошедшие выплаты. */
public record DividendsDto(
        String ticker,
        String source,
        List<DividendDto> upcoming,
        List<DividendDto> past
) {
}
