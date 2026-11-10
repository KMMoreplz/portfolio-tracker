package com.mikhail.portfolio.repository;

import com.mikhail.common.domain.Currency;
import com.mikhail.common.domain.Security;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Справочник ценных бумаг: поиск по тикеру/наименованию и upsert из операций. */
@Repository
public class SecurityRepository {

    private static final String COLUMNS = "id, ticker, name, currency, isin, created_at";

    private static final RowMapper<Security> ROW_MAPPER = (rs, rowNum) -> new Security(
            rs.getLong("id"),
            rs.getString("ticker"),
            rs.getString("name"),
            Currency.valueOf(rs.getString("currency")),
            rs.getString("isin"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbcClient;

    public SecurityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Добавляет бумагу, а при повторе тикера обновляет наименование — но только если
     * новое наименование не является заглушкой-тикером (тогда прежнее не затирается).
     */
    public void upsert(String ticker, String name, Currency currency, String isin) {
        jdbcClient.sql("""
                INSERT INTO securities (ticker, name, currency, isin)
                VALUES (:ticker, :name, :currency, :isin)
                ON CONFLICT (ticker) DO UPDATE
                SET name = CASE WHEN EXCLUDED.name <> EXCLUDED.ticker
                                THEN EXCLUDED.name ELSE securities.name END,
                    isin = COALESCE(EXCLUDED.isin, securities.isin)
                """)
                .param("ticker", ticker)
                .param("name", name)
                .param("currency", currency.name())
                .param("isin", isin)
                .update();
    }

    public Optional<Security> findByTicker(String ticker) {
        return jdbcClient.sql("SELECT %s FROM securities WHERE ticker = :ticker".formatted(COLUMNS))
                .param("ticker", ticker)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Поиск в справочнике по тикеру или наименованию (для подсказок в интерфейсе). */
    public List<Security> search(String query) {
        String pattern = "%" + query + "%";
        return jdbcClient.sql("""
                SELECT %s FROM securities
                WHERE ticker ILIKE :pattern OR name ILIKE :pattern
                ORDER BY ticker
                LIMIT 50
                """.formatted(COLUMNS))
                .param("pattern", pattern)
                .query(ROW_MAPPER)
                .list();
    }
}
