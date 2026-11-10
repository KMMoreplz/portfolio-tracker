package com.mikhail.telegrambot.portfolio;

import java.util.List;
import java.util.Optional;

/**
 * Чтение данных портфеля из portfolio-service (REST). Отдельный интерфейс,
 * чтобы бизнес-логика бота тестировалась на заглушке без запуска backend'а.
 */
public interface PortfolioClient {

    /** Портфель по идентификатору; empty, если portfolio-service отвечает 404. */
    Optional<PortfolioSnapshot> getPortfolio(long portfolioId);

    /**
     * Позиции портфеля.
     *
     * @throws com.mikhail.telegrambot.error.PortfolioNotFoundException если портфель не найден
     * @throws com.mikhail.telegrambot.error.PortfolioServiceUnavailableException если
     *         portfolio-service недоступен
     */
    List<PositionSnapshot> getPositions(long portfolioId);
}
