package com.mikhail.telegrambot.support;

import com.mikhail.telegrambot.error.PortfolioServiceUnavailableException;
import com.mikhail.telegrambot.error.TokenExpiredException;
import com.mikhail.telegrambot.portfolio.DividendCalendar;
import com.mikhail.telegrambot.portfolio.DividendItem;
import com.mikhail.telegrambot.portfolio.LinkResult;
import com.mikhail.telegrambot.portfolio.PortfolioClient;
import com.mikhail.telegrambot.portfolio.PortfolioInfo;
import com.mikhail.telegrambot.portfolio.PortfolioMetrics;
import com.mikhail.telegrambot.portfolio.PositionValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Тестовая заглушка portfolio-service: настраиваемые ответы и флаги
 * ошибок для проверки устойчивости бота.
 */
public class StubPortfolioClient implements PortfolioClient {

    public LinkResult linkResult;
    public boolean linkUnavailable;
    public final List<PortfolioInfo> portfolios = new ArrayList<>();
    public final Map<Long, PortfolioMetrics> metrics = new HashMap<>();
    public final Map<Long, DividendCalendar> calendars = new HashMap<>();
    public BigDecimal threshold;
    public boolean tokenExpired;
    public boolean unavailable;

    /** Удобный помощник: портфель с одной позицией-тикером. */
    public void addPortfolioWithTicker(long portfolioId, String ticker) {
        portfolios.add(new PortfolioInfo(portfolioId, "Портфель " + portfolioId, "RUB"));
        metrics.put(portfolioId, new PortfolioMetrics(portfolioId, "Портфель " + portfolioId, "RUB",
                new BigDecimal("1000"), new BigDecimal("900"), new BigDecimal("100"),
                new BigDecimal("11.11"), BigDecimal.ZERO,
                List.of(new PositionValue(ticker, null, new BigDecimal("1000"), "RUB",
                        new BigDecimal("11.11"), new BigDecimal("100")))));
    }

    @Override
    public Optional<LinkResult> link(String code) {
        if (linkUnavailable) {
            throw new PortfolioServiceUnavailableException("portfolio-service недоступен", null);
        }
        return Optional.ofNullable(linkResult);
    }

    @Override
    public List<PortfolioInfo> listPortfolios(String token) {
        check(token);
        return List.copyOf(portfolios);
    }

    @Override
    public Optional<PortfolioMetrics> metrics(String token, long portfolioId) {
        check(token);
        return Optional.ofNullable(metrics.get(portfolioId));
    }

    @Override
    public DividendCalendar dividendCalendar(String token, long portfolioId) {
        check(token);
        return calendars.getOrDefault(portfolioId,
                new DividendCalendar(portfolioId, "RUB", true, List.of()));
    }

    @Override
    public Optional<BigDecimal> priceAlertThreshold(String token) {
        check(token);
        return Optional.ofNullable(threshold);
    }

    private void check(String token) {
        if (unavailable) {
            throw new PortfolioServiceUnavailableException("portfolio-service недоступен", null);
        }
        if (tokenExpired) {
            throw new TokenExpiredException();
        }
    }

    /** Помощник: дивидендная выплата в календаре портфеля. */
    public void addDividend(long portfolioId, DividendItem item) {
        DividendCalendar existing = calendars.getOrDefault(portfolioId,
                new DividendCalendar(portfolioId, "RUB", true, List.of()));
        List<DividendItem> items = new ArrayList<>(existing.items());
        items.add(item);
        calendars.put(portfolioId, new DividendCalendar(portfolioId, "RUB", true, items));
    }
}
