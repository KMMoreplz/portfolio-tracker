package com.mikhail.marketdata.web.dto;

import com.mikhail.common.marketdata.Dividend;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Дивидендная выплата в ответе API.
 *
 * @param status UPCOMING — предстоящая, PAST — прошедшая
 */
public record DividendDto(
        String ticker,
        LocalDate declaredDate,
        LocalDate exDate,
        LocalDate recordDate,
        LocalDate paymentDate,
        BigDecimal amountPerShare,
        String currency,
        String regularity,
        String type,
        String status,
        String source
) {
}
