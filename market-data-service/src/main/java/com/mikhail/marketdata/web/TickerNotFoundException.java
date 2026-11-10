package com.mikhail.marketdata.web;

/** Тикер не найден ни у одного источника и отсутствует в сохранённых котировках. */
public class TickerNotFoundException extends RuntimeException {

    public TickerNotFoundException(String ticker) {
        super("Тикер " + ticker + " не найден");
    }
}
