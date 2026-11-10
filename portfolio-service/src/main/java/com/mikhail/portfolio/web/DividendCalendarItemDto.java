package com.mikhail.portfolio.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Предстоящая дивидендная выплата по бумаге из портфеля.
 *
 * @param quantity     сколько бумаг в портфеле
 * @param payout       amountPerShare × quantity в валюте выплаты
 * @param payoutInBase то же в базовой валюте портфеля, null — курс неизвестен
 * @param source       откуда взята выплата (источник market-data-service)
 */
public record DividendCalendarItemDto(
        String ticker,
        String instrumentName,
        BigDecimal quantity,
        LocalDate exDate,
        LocalDate paymentDate,
        BigDecimal amountPerShare,
        String currency,
        BigDecimal payout,
        BigDecimal payoutInBase,
        String source
) {
}
