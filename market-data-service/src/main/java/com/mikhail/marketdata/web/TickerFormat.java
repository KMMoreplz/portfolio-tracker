package com.mikhail.marketdata.web;

import java.util.Locale;
import java.util.regex.Pattern;

/** Проверка формата тикера: как в ограничениях portfolio-service. */
public final class TickerFormat {

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,31}$");

    private TickerFormat() {
    }

    /** Нормализует тикер (trim + верхний регистр) и проверяет формат. */
    public static String normalize(String rawTicker) {
        if (rawTicker == null) {
            throw new IllegalArgumentException("Тикер не указан");
        }
        String ticker = rawTicker.trim().toUpperCase(Locale.ROOT);
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new IllegalArgumentException("Некорректный тикер: " + rawTicker);
        }
        return ticker;
    }
}
