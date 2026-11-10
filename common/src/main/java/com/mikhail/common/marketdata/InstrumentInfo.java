package com.mikhail.common.marketdata;

/**
 * Справочная информация об инструменте.
 *
 * @param ticker   тикер, например SBER
 * @param name     краткое наименование, например Сбербанк
 * @param isin     ISIN или null, если у источника нет данных
 * @param currency ISO-код валюты инструмента
 * @param lotSize  размер лота в штуках
 * @param source   источник данных
 */
public record InstrumentInfo(
        String ticker,
        String name,
        String isin,
        String currency,
        int lotSize,
        String source
) {
}
