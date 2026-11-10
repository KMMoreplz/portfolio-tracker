package com.mikhail.portfolio.repository;

import com.mikhail.common.marketdata.Quote;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Локальный кэш котировок: хранит цены, по которым считалась оценка портфеля.
 * Ключ (ticker, quoted_at) делает запись идемпотентной при повторных расчётах.
 */
@Repository
public class PriceQuoteRepository {

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

    public void insertIfAbsent(Quote quote) {
        jdbcClient.sql("""
                INSERT INTO price_quotes (ticker, price, currency, quoted_at, source)
                VALUES (:ticker, :price, :currency, :quotedAt, :source)
                ON CONFLICT (ticker, quoted_at) DO NOTHING
                """)
                .param("ticker", quote.ticker())
                .param("price", quote.price())
                .param("currency", quote.currency())
                .param("quotedAt", OffsetDateTime.ofInstant(quote.quotedAt(), ZoneOffset.UTC))
                .param("source", quote.source())
                .update();
    }

    /** Последняя сохранённая котировка по тикеру. */
    public Optional<Quote> findLatestByTicker(String ticker) {
        return jdbcClient.sql("""
                SELECT ticker, price, currency, quoted_at, source
                FROM price_quotes
                WHERE ticker = :ticker
                ORDER BY quoted_at DESC
                LIMIT 1
                """)
                .param("ticker", ticker)
                .query(ROW_MAPPER)
                .optional();
    }
}
