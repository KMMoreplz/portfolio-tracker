package com.mikhail.portfolio.model;

import java.time.OffsetDateTime;

public class Portfolio {

    private final Long id;
    private final String name;
    private final String baseCurrency;
    private final OffsetDateTime createdAt;

    public Portfolio(Long id, String name, String baseCurrency, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.baseCurrency = baseCurrency;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
