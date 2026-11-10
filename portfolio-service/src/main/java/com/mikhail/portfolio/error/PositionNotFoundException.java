package com.mikhail.portfolio.error;

public class PositionNotFoundException extends RuntimeException {

    public PositionNotFoundException(Long portfolioId, Long positionId) {
        super("Позиция с id=" + positionId + " в портфеле с id=" + portfolioId + " не найдена");
    }
}
