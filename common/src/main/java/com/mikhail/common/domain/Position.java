package com.mikhail.common.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Позиция — количество бумаги одного тикера в портфеле.
 *
 * @param id             идентификатор позиции
 * @param portfolioId    идентификатор портфеля
 * @param ticker         тикер инструмента
 * @param instrumentName краткое наименование инструмента, может быть {@code null}
 * @param quantity       количество бумаг, больше нуля
 * @param averagePrice   средневзвешенная цена покупки с учётом комиссий
 * @param currency       валюта средней цены
 * @param updatedAt      время последнего изменения
 */
public record Position(
        Long id,
        Long portfolioId,
        String ticker,
        String instrumentName,
        BigDecimal quantity,
        BigDecimal averagePrice,
        Currency currency,
        OffsetDateTime updatedAt
) {
    public Position {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(averagePrice, "averagePrice");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Стоимость позиции по средней цене покупки (вложенные деньги). */
    public BigDecimal costAmount() {
        return quantity.multiply(averagePrice);
    }
}
