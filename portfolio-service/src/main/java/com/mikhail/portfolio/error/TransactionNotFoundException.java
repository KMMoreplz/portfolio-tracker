package com.mikhail.portfolio.error;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(Long id) {
        super("Операция с id=" + id + " не найдена в этом портфеле");
    }
}
