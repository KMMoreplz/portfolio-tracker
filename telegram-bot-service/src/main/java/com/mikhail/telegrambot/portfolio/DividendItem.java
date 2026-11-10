package com.mikhail.telegrambot.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Предстоящая дивидендная выплата из дивидендного календаря портфеля
 * (данные календаря портфель берёт у market-data-service).
 */
public record DividendItem(
        String ticker,
        String instrumentName,
        LocalDate paymentDate,
        LocalDate exDate,
        BigDecimal amountPerShare,
        String currency,
        BigDecimal payout,
        BigDecimal payoutInBase
) {
}
