package com.mikhail.marketdata.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Тикеры, за которыми следит сервис: собственная таблица {@code market_data.tracked_tickers}
 * плюс список из конфигурации {@code market-data.tickers}.
 */
@Repository
public class TrackedTickerRepository {

    private static final String SCHEMA = "market_data.";

    private final JdbcClient jdbcClient;

    public TrackedTickerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<String> findAll() {
        return jdbcClient.sql("SELECT ticker FROM %stracked_tickers ORDER BY ticker".formatted(SCHEMA))
                .query(String.class)
                .list();
    }

    public void add(String ticker) {
        jdbcClient.sql("""
                INSERT INTO %stracked_tickers (ticker)
                VALUES (:ticker)
                ON CONFLICT (ticker) DO NOTHING
                """.formatted(SCHEMA))
                .param("ticker", ticker)
                .update();
    }

    public boolean delete(String ticker) {
        return jdbcClient.sql("DELETE FROM %stracked_tickers WHERE ticker = :ticker".formatted(SCHEMA))
                .param("ticker", ticker)
                .update() > 0;
    }
}
