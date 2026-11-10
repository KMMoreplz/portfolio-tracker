package com.mikhail.portfolio.web;

import com.mikhail.portfolio.model.Position;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PositionDto(Long id, Long portfolioId, String ticker, String instrumentName,
                          BigDecimal quantity, BigDecimal averagePrice, String currency,
                          OffsetDateTime updatedAt) {

    public static PositionDto from(Position position) {
        return new PositionDto(
                position.getId(),
                position.getPortfolioId(),
                position.getTicker(),
                position.getInstrumentName(),
                position.getQuantity(),
                position.getAveragePrice(),
                position.getCurrency(),
                position.getUpdatedAt());
    }
}
