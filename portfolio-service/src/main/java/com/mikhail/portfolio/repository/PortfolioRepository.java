package com.mikhail.portfolio.repository;

import com.mikhail.portfolio.model.Portfolio;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PortfolioRepository {

    private static final String COLUMNS = "id, name, base_currency, created_at";

    private static final RowMapper<Portfolio> ROW_MAPPER = (rs, rowNum) -> new Portfolio(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("base_currency"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbcClient;

    public PortfolioRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Portfolio insert(String name, String baseCurrency) {
        return jdbcClient.sql("""
                INSERT INTO portfolio (name, base_currency)
                VALUES (:name, :baseCurrency)
                RETURNING %s
                """.formatted(COLUMNS))
                .param("name", name)
                .param("baseCurrency", baseCurrency)
                .query(ROW_MAPPER)
                .single();
    }

    public Optional<Portfolio> findById(Long id) {
        return jdbcClient.sql("SELECT %s FROM portfolio WHERE id = :id".formatted(COLUMNS))
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<Portfolio> findAll() {
        return jdbcClient.sql("SELECT %s FROM portfolio ORDER BY id".formatted(COLUMNS))
                .query(ROW_MAPPER)
                .list();
    }

    public Optional<Portfolio> update(Long id, String name, String baseCurrency) {
        return jdbcClient.sql("""
                UPDATE portfolio
                SET name = :name, base_currency = :baseCurrency
                WHERE id = :id
                RETURNING %s
                """.formatted(COLUMNS))
                .param("id", id)
                .param("name", name)
                .param("baseCurrency", baseCurrency)
                .query(ROW_MAPPER)
                .optional();
    }

    public boolean deleteById(Long id) {
        int deleted = jdbcClient.sql("DELETE FROM portfolio WHERE id = :id")
                .param("id", id)
                .update();
        return deleted > 0;
    }
}
