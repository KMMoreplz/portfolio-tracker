package com.mikhail.portfolio.repository;

import com.mikhail.common.domain.Currency;
import com.mikhail.common.domain.Transaction;
import com.mikhail.common.domain.TransactionType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Операции портфеля. Строка таблицы transactions читается и пишется
 * как общий DTO {@link Transaction} из модуля common.
 */
@Repository
public class TransactionRepository {

    private static final String COLUMNS =
            "id, portfolio_id, type, ticker, quantity, price, commission, currency, executed_at";

    private static final RowMapper<Transaction> ROW_MAPPER = (rs, rowNum) -> new Transaction(
            rs.getLong("id"),
            rs.getLong("portfolio_id"),
            TransactionType.valueOf(rs.getString("type")),
            rs.getString("ticker"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("commission"),
            Currency.valueOf(rs.getString("currency")),
            rs.getObject("executed_at", OffsetDateTime.class));

    private final JdbcClient jdbcClient;

    public TransactionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Transaction insert(Long portfolioId, TransactionType type, String ticker,
                              java.math.BigDecimal quantity, java.math.BigDecimal price,
                              java.math.BigDecimal commission, Currency currency,
                              OffsetDateTime executedAt) {
        return jdbcClient.sql("""
                INSERT INTO transactions (portfolio_id, type, ticker, quantity, price, commission, currency, executed_at)
                VALUES (:portfolioId, :type, :ticker, :quantity, :price, :commission, :currency, :executedAt)
                RETURNING %s
                """.formatted(COLUMNS))
                .param("portfolioId", portfolioId)
                .param("type", type.name())
                .param("ticker", ticker)
                .param("quantity", quantity)
                .param("price", price)
                .param("commission", commission)
                .param("currency", currency.name())
                .param("executedAt", executedAt)
                .query(ROW_MAPPER)
                .single();
    }

    public Transaction update(Long id, Long portfolioId, TransactionType type, String ticker,
                              java.math.BigDecimal quantity, java.math.BigDecimal price,
                              java.math.BigDecimal commission, Currency currency,
                              OffsetDateTime executedAt) {
        return jdbcClient.sql("""
                UPDATE transactions
                SET type = :type,
                    ticker = :ticker,
                    quantity = :quantity,
                    price = :price,
                    commission = :commission,
                    currency = :currency,
                    executed_at = :executedAt
                WHERE id = :id AND portfolio_id = :portfolioId
                RETURNING %s
                """.formatted(COLUMNS))
                .param("id", id)
                .param("portfolioId", portfolioId)
                .param("type", type.name())
                .param("ticker", ticker)
                .param("quantity", quantity)
                .param("price", price)
                .param("commission", commission)
                .param("currency", currency.name())
                .param("executedAt", executedAt)
                .query(ROW_MAPPER)
                .single();
    }

    public Optional<Transaction> findById(Long portfolioId, Long id) {
        return jdbcClient.sql("SELECT %s FROM transactions WHERE id = :id AND portfolio_id = :portfolioId"
                        .formatted(COLUMNS))
                .param("id", id)
                .param("portfolioId", portfolioId)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Все операции портфеля; {@code type = null} — без фильтра по виду операции. */
    public List<Transaction> findByPortfolioId(Long portfolioId, TransactionType type) {
        String columns = COLUMNS;
        if (type == null) {
            return jdbcClient.sql("""
                    SELECT %s FROM transactions
                    WHERE portfolio_id = :portfolioId
                    ORDER BY executed_at DESC, id DESC
                    """.formatted(columns))
                    .param("portfolioId", portfolioId)
                    .query(ROW_MAPPER)
                    .list();
        }
        return jdbcClient.sql("""
                SELECT %s FROM transactions
                WHERE portfolio_id = :portfolioId AND type = :type
                ORDER BY executed_at DESC, id DESC
                """.formatted(columns))
                .param("portfolioId", portfolioId)
                .param("type", type.name())
                .query(ROW_MAPPER)
                .list();
    }

    public boolean delete(Long portfolioId, Long id) {
        int deleted = jdbcClient.sql("""
                DELETE FROM transactions
                WHERE id = :id AND portfolio_id = :portfolioId
                """)
                .param("id", id)
                .param("portfolioId", portfolioId)
                .update();
        return deleted > 0;
    }
}
