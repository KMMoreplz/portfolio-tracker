package com.mikhail.portfolio.service;

import com.mikhail.common.domain.Currency;
import com.mikhail.common.domain.Money;
import com.mikhail.portfolio.error.NoExchangeRateException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Заглушка курсов валют: фиксированные курсы к рублю (по ним пересчитываются итоги портфеля).
 * Реальные курсы подключаются отдельной задачей — здесь важно только место расширения
 * и честная ошибка {@link NoExchangeRateException} для неизвестной валюты.
 */
@Component
public class InMemoryExchangeRateProvider {

    private static final Map<Currency, BigDecimal> RATE_TO_RUB = Map.ofEntries(
            Map.entry(Currency.RUB, BigDecimal.ONE),
            Map.entry(Currency.USD, new BigDecimal("90.50")),
            Map.entry(Currency.EUR, new BigDecimal("99.50")),
            Map.entry(Currency.CNY, new BigDecimal("12.80")),
            Map.entry(Currency.GBP, new BigDecimal("116.00")),
            Map.entry(Currency.CHF, new BigDecimal("101.00")),
            Map.entry(Currency.JPY, new BigDecimal("0.60")),
            Map.entry(Currency.KZT, new BigDecimal("0.19")),
            Map.entry(Currency.TRY, new BigDecimal("2.60")),
            Map.entry(Currency.HKD, new BigDecimal("11.60")),
            Map.entry(Currency.BYN, new BigDecimal("28.50")),
            Map.entry(Currency.AMD, new BigDecimal("0.23")),
            Map.entry(Currency.KGS, new BigDecimal("1.04")),
            Map.entry(Currency.UZS, new BigDecimal("0.072")));

    /** Пересчитывает сумму в целевую валюту; курсы берутся к рублю-посреднику. */
    public Money convert(Money money, Currency target) {
        if (money.currency() == target) {
            return money;
        }
        BigDecimal from = RATE_TO_RUB.get(money.currency());
        BigDecimal to = RATE_TO_RUB.get(target);
        if (from == null) {
            throw new NoExchangeRateException(money.currency().getCode());
        }
        if (to == null) {
            throw new NoExchangeRateException(target.getCode());
        }
        BigDecimal rate = from.divide(to, 6, RoundingMode.HALF_UP);
        return money.convertedTo(rate, target);
    }
}
