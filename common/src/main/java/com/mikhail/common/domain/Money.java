package com.mikhail.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Денежная сумма в конкретной валюте. Суммы никогда не хранятся как double —
 * только {@link BigDecimal}, чтобы не терять точность.
 *
 * @param amount   сумма, не может быть {@code null}
 * @param currency валюта суммы, не может быть {@code null}
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    /** Сумма с той же валютой; бросает {@link IllegalArgumentException} при разных валютах. */
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /** Разность с той же валютой; бросает {@link IllegalArgumentException} при разных валютах. */
    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multipliedBy(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    /**
     * Пересчёт в другую валюту по заданному курсу.
     *
     * @param rate          сколько единиц целевой валюты за одну единицу исходной
     * @param targetCurrency целевая валюта
     */
    public Money convertedTo(BigDecimal rate, Currency targetCurrency) {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(targetCurrency, "targetCurrency");
        return new Money(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP), targetCurrency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "Операции над денежными суммами требуют одинаковой валюты: %s и %s".formatted(
                            currency.getCode(), other.currency.getCode()));
        }
    }
}
