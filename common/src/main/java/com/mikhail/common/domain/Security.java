package com.mikhail.common.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Ценная бумага — запись справочника инструментов (акция, облигация, фонд).
 *
 * @param id        идентификатор в базе данных
 * @param ticker    тикер, например SBER
 * @param name      краткое наименование, например Сбербанк
 * @param currency  валюта, в которой торгуется инструмент
 * @param isin      международный код ISIN, может быть {@code null}, если источник его не знает
 * @param createdAt время записи в справочник
 */
public record Security(
        Long id,
        String ticker,
        String name,
        Currency currency,
        String isin,
        OffsetDateTime createdAt
) {
    public Security {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(currency, "currency");
    }
}
