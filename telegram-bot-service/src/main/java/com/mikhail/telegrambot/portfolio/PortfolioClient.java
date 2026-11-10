package com.mikhail.telegrambot.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Чтение данных из portfolio-service (REST) от имени привязанного
 * пользователя. Отдельный интерфейс, чтобы бизнес-логика бота
 * тестировалась на заглушке без запуска backend'а.
 *
 * <p>Методы с токеном выполняют запросы с заголовком Authorization: Bearer.
 *
 * @throws com.mikhail.telegrambot.error.TokenExpiredException                если portfolio-service
 *                                                                           отклонил токен (401/403)
 * @throws com.mikhail.telegrambot.error.PortfolioServiceUnavailableException если
 *                                                                           portfolio-service недоступен
 */
public interface PortfolioClient {

    /**
     * Погашает одноразовый код привязки из веб-приложения.
     *
     * @return данные пользователя и выданный токен; пусто, если код недействителен
     */
    Optional<LinkResult> link(String code);

    /** Портфели пользователя. */
    List<PortfolioInfo> listPortfolios(String token);

    /** Метрики портфеля; пусто, если портфель не найден. */
    Optional<PortfolioMetrics> metrics(String token, long portfolioId);

    /** Дивидендный календарь портфеля (данные market-data-service через portfolio-service). */
    DividendCalendar dividendCalendar(String token, long portfolioId);

    /** Порог алертов из настроек пользователя; пусто, если не задан. */
    Optional<BigDecimal> priceAlertThreshold(String token);
}
