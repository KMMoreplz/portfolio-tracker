package com.mikhail.portfolio.model;

import java.math.BigDecimal;

/** Сумма дивидендов по одной валюте за весь период портфеля (для метрик). */
public record DividendTotal(String currency, BigDecimal amount) {
}
