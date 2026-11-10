package com.mikhail.portfolio.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Position {

    private final Long id;
    private final Long portfolioId;
    private final String ticker;
    private final String instrumentName;
    private final BigDecimal quantity;
    private final BigDecimal averagePrice;
    private final String currency;
    private final OffsetDateTime updatedAt;

    public Position(Long id, Long portfolioId, String ticker, String instrumentName,
                    BigDecimal quantity, BigDecimal averagePrice, String currency, OffsetDateTime updatedAt) {
        this.id = id;
        this.portfolioId = portfolioId;
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.currency = currency;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public String getTicker() {
        return ticker;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
