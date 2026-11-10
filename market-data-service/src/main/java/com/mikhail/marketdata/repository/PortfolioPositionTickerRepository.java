package com.mikhail.marketdata.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Чтение тикеров из позиций портфеля (таблица {@code public.position} из
 * portfolio-service). Общая БД, но своя схема: таблица читается только на чтение,
 * и если её ещё нет (миграции portfolio-service не выполнялись), источник
 * спокойно возвращает пустой список.
 */
@Repository
public class PortfolioPositionTickerRepository {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPositionTickerRepository.class);

    private final JdbcClient jdbcClient;
    private volatile Boolean positionTableAvailable;

    public PortfolioPositionTickerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<String> findTickers() {
        if (!positionTableAvailable()) {
            return List.of();
        }
        try {
            return jdbcClient.sql("SELECT DISTINCT ticker FROM public.position ORDER BY ticker")
                    .query(String.class)
                    .list();
        } catch (RuntimeException exception) {
            log.warn("Не удалось прочитать тикеры из public.position: {}", exception.getMessage());
            positionTableAvailable = false;
            return List.of();
        }
    }

    private boolean positionTableAvailable() {
        Boolean available = positionTableAvailable;
        if (available != null) {
            return available;
        }
        synchronized (this) {
            if (positionTableAvailable != null) {
                return positionTableAvailable;
            }
            positionTableAvailable = jdbcClient.sql("""
                    SELECT EXISTS(
                        SELECT 1 FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'position'
                    )
                    """).query(Boolean.class).single();
            return positionTableAvailable;
        }
    }
}
