package com.mikhail.telegrambot.portfolio;

import java.math.BigDecimal;

/** Снимок позиции портфеля из portfolio-service. */
public record PositionSnapshot(String ticker, String instrumentName,
                               BigDecimal quantity, BigDecimal averagePrice, String currency) {
}
