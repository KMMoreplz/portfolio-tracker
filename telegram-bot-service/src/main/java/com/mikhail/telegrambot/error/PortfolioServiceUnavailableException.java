package com.mikhail.telegrambot.error;

public class PortfolioServiceUnavailableException extends RuntimeException {

    public PortfolioServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
