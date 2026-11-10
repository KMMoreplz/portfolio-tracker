package com.mikhail.portfolio.error;

/** Продажа большего количества бумаг, чем есть в портфеле (или продажа без позиции). */
public class InsufficientQuantityException extends RuntimeException {

    public InsufficientQuantityException(String message) {
        super(message);
    }
}
