package com.mikhail.portfolio.repository;

import com.mikhail.portfolio.model.DividendTotal;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** События дивидендов: по одной записи на выплату (создаётся при операции типа DIVIDEND). */
@Repository
public class DividendEventRepository {

    private static final RowMapper<DividendTotal> TOTAL_ROW_MAPPER =
            (rs, rowNum) -> new DividendTotal(rs.getString("currency"), rs.getBigDecimal("amount"));

    private final JdbcClient jdbcClient;

    public DividendEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(Long portfolioId, Long transactionId, String ticker, LocalDate payDate,
                       java.math.BigDecimal amountPerShare, java.math.BigDecimal quantity,
                       java.math.BigDecimal totalAmount, String currency) {
        jdbcClient.sql("""
                INSERT INTO dividend_events
                    (portfolio_id, transaction_id, ticker, pay_date,
                     amount_per_share, quantity, total_amount, currency)
                VALUES (:portfolioId, :transactionId, :ticker, :payDate,
                        :amountPerShare, :quantity, :totalAmount, :currency)
                """)
                .param("portfolioId", portfolioId)
                .param("transactionId", transactionId)
                .param("ticker", ticker)
                .param("payDate", payDate)
                .param("amountPerShare", amountPerShare)
                .param("quantity", quantity)
                .param("totalAmount", totalAmount)
                .param("currency", currency)
                .update();
    }

    public void deleteByTransactionId(Long transactionId) {
        jdbcClient.sql("DELETE FROM dividend_events WHERE transaction_id = :transactionId")
                .param("transactionId", transactionId)
                .update();
    }

    /** Полученные дивиденды, сгруппированные по валюте. */
    public List<DividendTotal> totalsByPortfolioId(Long portfolioId) {
        return jdbcClient.sql("""
                SELECT currency, SUM(total_amount) AS amount
                FROM dividend_events
                WHERE portfolio_id = :portfolioId
                GROUP BY currency
                ORDER BY currency
                """)
                .param("portfolioId", portfolioId)
                .query(TOTAL_ROW_MAPPER)
                .list();
    }
}
