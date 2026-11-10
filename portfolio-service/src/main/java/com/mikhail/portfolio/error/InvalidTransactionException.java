package com.mikhail.portfolio.error;

/** Семантически некорректная операция: неизвестный тикер, неизвестная валюта, валютный дисбаланс. */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
