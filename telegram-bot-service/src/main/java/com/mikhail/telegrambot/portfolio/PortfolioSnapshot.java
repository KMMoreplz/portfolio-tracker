package com.mikhail.telegrambot.portfolio;

import java.math.BigDecimal;

/** Снимок портфеля из portfolio-service: идентификатор, название, базовая валюта. */
public record PortfolioSnapshot(long id, String name, String baseCurrency) {
}
