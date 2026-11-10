package com.mikhail.telegrambot.error;

public class PortfolioNotFoundException extends RuntimeException {

    private final long portfolioId;

    public PortfolioNotFoundException(long portfolioId) {
        super("Портфель #" + portfolioId + " не найден");
        this.portfolioId = portfolioId;
    }

    public long getPortfolioId() {
        return portfolioId;
    }
}
