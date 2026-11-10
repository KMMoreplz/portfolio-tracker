package com.mikhail.portfolio.marketdata;

/**
 * market-data-service недоступен (нет адреса, таймаут, ошибка соединения или 5xx).
 * Вызывающий код честно деградирует: календарь отдаётся пустым с marketDataAvailable=false,
 * история стоимости считается по сохранённым котировкам и средним ценам покупки.
 */
public class MarketDataUnavailableException extends RuntimeException {

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public MarketDataUnavailableException(String message) {
        super(message);
    }
}
