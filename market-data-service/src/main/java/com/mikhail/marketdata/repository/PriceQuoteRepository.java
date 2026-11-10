package com.mikhail.marketdata.repository;

import com.mikhail.common.marketdata.Quote;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Котировки в схеме {@code market_data} общей базы данных portfolio_db.
 * Таблица append-only: каждое обновление добавляет строку, «последняя» котировка —
 * строка с максимальным quoted_at.
 */
@Repository
public class PriceQuoteRepository {

    private static final String SCHEMA = "market_data.";
    private static final String COLUMNS = "ticker, price, currency, quoted_at, source";

    private static final RowMapper<Quote> ROW_MAPPER = (rs, rowNum) -> new Quote(
            rs.getString("ticker"),
            rs.getBigDecimal("price"),
            rs.getString("currency"),
            rs.getObject("quoted_at", OffsetDateTime.class).toInstant(),
            rs.getString("source"));

    private final JdbcClient jdbcClient;

    public PriceQuoteRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(Quote quote) {
        jdbcClient.sql("""
                INSERT INTO %sprice_quotes (ticker, price, currency, quoted_at, source)
                VALUES (:ticker, :price, :currency, :quotedAt, :source)
                """.formatted(SCHEMA))
                .param("ticker", quote.ticker())
                .param("price", quote.price())
                .param("currency", quote.currency())
                .param("quotedAt", OffsetDateTime.ofInstant(quote.quotedAt(), ZoneOffset.UTC))
                .param("source", quote.source())
                .update();
    }

    public Optional<Quote> findLatest(String ticker) {
        return jdbcClient.sql("""
                SELECT %s FROM %sprice_quotes
                WHERE ticker = :ticker
                ORDER BY quoted_at DESC, captured_at DESC
                LIMIT 1
                """.formatted(COLUMNS, SCHEMA))
                .param("ticker", ticker)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<Quote> findLatest(Collection<String> tickers) {
        if (tickers.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                SELECT DISTINCT ON (ticker) %s FROM %sprice_quotes
                WHERE ticker IN (:tickers)
                ORDER BY ticker, quoted_at DESC, captured_at DESC
                """.formatted(COLUMNS, SCHEMA))
                .param("tickers", tickers)
                .query(ROW_MAPPER)
                .list();
    }

    public List<Quote> findHistory(String ticker, Instant from, Instant to) {
        return jdbcClient.sql("""
                SELECT %s FROM %sprice_quotes
                WHERE ticker = :ticker AND quoted_at >= :from AND quoted_at <= :to
                ORDER BY quoted_at
                """.formatted(COLUMNS, SCHEMA))
                .param("ticker", ticker)
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .query(ROW_MAPPER)
                .list();
    }
}
