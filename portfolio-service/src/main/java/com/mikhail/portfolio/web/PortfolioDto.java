package com.mikhail.portfolio.web;

import com.mikhail.portfolio.model.Portfolio;

import java.time.OffsetDateTime;

public record PortfolioDto(Long id, String name, String baseCurrency, OffsetDateTime createdAt) {

    public static PortfolioDto from(Portfolio portfolio) {
        return new PortfolioDto(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getBaseCurrency(),
                portfolio.getCreatedAt());
    }
}
