package com.mikhail.common.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Операция над портфелем: покупка, продажа или получение дивиденда.
 *
 * @param id          идентификатор операции
 * @param portfolioId идентификатор портфеля
 * @param type        вид операции
 * @param ticker      тикер инструмента
 * @param quantity    количество бумаг (для дивиденда — количество бумаг, по которым начислен доход)
 * @param price       цена за одну бумагу (для дивиденда — дивиденд на одну бумагу)
 * @param commission  комиссия брокера за операцию, ноль, если не указана
 * @param currency    валюта операции
 * @param executedAt  время исполнения операции
 */
public record Transaction(
        Long id,
        Long portfolioId,
        TransactionType type,
        String ticker,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal commission,
        Currency currency,
        OffsetDateTime executedAt
) {
    public Transaction {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(executedAt, "executedAt");
        if (commission == null) {
            commission = BigDecimal.ZERO;
        }
    }

    /** Сумма операции без комиссии: количество × цена. */
    public BigDecimal grossAmount() {
        return quantity.multiply(price);
    }

    /** Сумма операции с учётом комиссии. */
    public BigDecimal netAmount() {
        return grossAmount().add(commission);
    }
}
