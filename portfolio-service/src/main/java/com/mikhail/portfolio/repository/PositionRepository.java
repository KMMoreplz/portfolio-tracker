package com.mikhail.portfolio.repository;

import com.mikhail.portfolio.model.Position;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PositionRepository {

    private static final String COLUMNS =
            "id, portfolio_id, ticker, instrument_name, quantity, average_price, currency, updated_at";

    private static final RowMapper<Position> ROW_MAPPER = (rs, rowNum) -> new Position(
            rs.getLong("id"),
            rs.getLong("portfolio_id"),
            rs.getString("ticker"),
            rs.getString("instrument_name"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("average_price"),
            rs.getString("currency"),
            rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcClient jdbcClient;

    public PositionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Position insert(Long portfolioId, String ticker, String instrumentName,
                           BigDecimal quantity, BigDecimal averagePrice, String currency) {
        return jdbcClient.sql("""
                INSERT INTO position (portfolio_id, ticker, instrument_name, quantity, average_price, currency)
                VALUES (:portfolioId, :ticker, :instrumentName, :quantity, :averagePrice, :currency)
                RETURNING %s
                """.formatted(COLUMNS))
                .param("portfolioId", portfolioId)
                .param("ticker", ticker)
                .param("instrumentName", instrumentName)
                .param("quantity", quantity)
                .param("averagePrice", averagePrice)
                .param("currency", currency)
                .query(ROW_MAPPER)
                .single();
    }

    public List<Position> findByPortfolioId(Long portfolioId) {
        return jdbcClient.sql("""
                SELECT %s FROM position
                WHERE portfolio_id = :portfolioId
                ORDER BY ticker
                """.formatted(COLUMNS))
                .param("portfolioId", portfolioId)
                .query(ROW_MAPPER)
                .list();
    }

    public Optional<Position> findByPortfolioIdAndTicker(Long portfolioId, String ticker) {
        return jdbcClient.sql("""
                SELECT %s FROM position
                WHERE portfolio_id = :portfolioId AND ticker = :ticker
                """.formatted(COLUMNS))
                .param("portfolioId", portfolioId)
                .param("ticker", ticker)
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<Position> findById(Long portfolioId, Long positionId) {
        return jdbcClient.sql("""
                SELECT %s FROM position
                WHERE id = :positionId AND portfolio_id = :portfolioId
                """.formatted(COLUMNS))
                .param("portfolioId", portfolioId)
                .param("positionId", positionId)
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<Position> update(Long portfolioId, Long positionId, String instrumentName,
                                     BigDecimal quantity, BigDecimal averagePrice, String currency) {
        return jdbcClient.sql("""
                UPDATE position
                SET instrument_name = :instrumentName,
                    quantity = :quantity,
                    average_price = :averagePrice,
                    currency = :currency,
                    updated_at = now()
                WHERE id = :positionId AND portfolio_id = :portfolioId
                RETURNING %s
                """.formatted(COLUMNS))
                .param("portfolioId", portfolioId)
                .param("positionId", positionId)
                .param("instrumentName", instrumentName)
                .param("quantity", quantity)
                .param("averagePrice", averagePrice)
                .param("currency", currency)
                .query(ROW_MAPPER)
                .optional();
    }

    public boolean delete(Long portfolioId, Long positionId) {
        int deleted = jdbcClient.sql("""
                DELETE FROM position
                WHERE id = :positionId AND portfolio_id = :portfolioId
                """)
                .param("portfolioId", portfolioId)
                .param("positionId", positionId)
                .update();
        return deleted > 0;
    }
}
