package com.mikhail.portfolio.error;

public class PortfolioNotFoundException extends RuntimeException {

    public PortfolioNotFoundException(Long id) {
        super("Портфель с id=" + id + " не найден");
    }
}
