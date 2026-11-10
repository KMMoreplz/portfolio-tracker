package com.mikhail.portfolio.error;

public class PositionAlreadyExistsException extends RuntimeException {

    public PositionAlreadyExistsException(Long portfolioId, String ticker) {
        super("Позиция с тикером " + ticker + " в портфеле с id=" + portfolioId + " уже существует");
    }
}
