package com.mikhail.telegrambot.portfolio;

/** Портфель из portfolio-service: id, название и базовая валюта. */
public record PortfolioInfo(Long id, String name, String baseCurrency) {
}
