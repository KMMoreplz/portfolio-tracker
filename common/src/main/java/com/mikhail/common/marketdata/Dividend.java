package com.mikhail.common.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Дивидендная выплата.
 *
 * @param ticker        тикер инструмента
 * @param declaredDate  дата объявления или null, если источник не сообщил
 * @param exDate        последний день покупки (экспир-дата) или null
 * @param recordDate    дата закрытия реестра или null
 * @param paymentDate   дата выплаты или null
 * @param amountPerShare сумма на акцию или null, если источник не сообщил
 * @param currency      ISO-код валюты выплаты
 * @param regularity    регулярность (Annual, Semi-Anl, ...), если известна
 * @param type          тип выплаты (Regular Cash, ...), если известен
 * @param source        источник данных
 */
public record Dividend(
        String ticker,
        LocalDate declaredDate,
        LocalDate exDate,
        LocalDate recordDate,
        LocalDate paymentDate,
        BigDecimal amountPerShare,
        String currency,
        String regularity,
        String type,
        String source
) {
}
