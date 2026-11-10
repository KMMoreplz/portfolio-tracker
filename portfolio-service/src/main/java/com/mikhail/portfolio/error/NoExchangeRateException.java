package com.mikhail.portfolio.error;

/** Нет курса конвертации для валюты: расчёт итогов портфеля невозможен. */
public class NoExchangeRateException extends RuntimeException {

    public NoExchangeRateException(String currency) {
        super("Неизвестен курс валюты " + currency + " — расчёт итогов невозможен");
    }
}
