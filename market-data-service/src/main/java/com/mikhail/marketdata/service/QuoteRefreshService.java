package com.mikhail.marketdata.service;

import com.mikhail.common.marketdata.Quote;
import com.mikhail.marketdata.config.MarketDataProperties;
import com.mikhail.marketdata.event.PriceChangedEvent;
import com.mikhail.marketdata.event.PriceEventPublisher;
import com.mikhail.marketdata.repository.PortfolioPositionTickerRepository;
import com.mikhail.marketdata.repository.PriceQuoteRepository;
import com.mikhail.marketdata.repository.TrackedTickerRepository;
import com.mikhail.marketdata.web.dto.RefreshResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Плановое и ручное обновление котировок по всем отслеживаемым бумагам:
 * тикеры из конфигурации + таблицы tracked_tickers + позиций портфеля.
 * Каждая котировка сохраняется в price_quotes, значимое изменение публикуется
 * событием через вебхук.
 */
@Service
public class QuoteRefreshService {

    private static final Logger log = LoggerFactory.getLogger(QuoteRefreshService.class);

    private final MarketDataProperties properties;
    private final QuoteService quoteService;
    private final PriceQuoteRepository priceQuoteRepository;
    private final TrackedTickerRepository trackedTickerRepository;
    private final PortfolioPositionTickerRepository positionTickerRepository;
    private final PriceEventPublisher priceEventPublisher;

    public QuoteRefreshService(
            MarketDataProperties properties,
            QuoteService quoteService,
            PriceQuoteRepository priceQuoteRepository,
            TrackedTickerRepository trackedTickerRepository,
            PortfolioPositionTickerRepository positionTickerRepository,
            PriceEventPublisher priceEventPublisher) {
        this.properties = properties;
        this.quoteService = quoteService;
        this.priceQuoteRepository = priceQuoteRepository;
        this.trackedTickerRepository = trackedTickerRepository;
        this.positionTickerRepository = positionTickerRepository;
        this.priceEventPublisher = priceEventPublisher;
    }

    public RefreshResultDto refreshAll() {
        List<String> refreshed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String ticker : trackedTickers()) {
            try {
                Optional<Quote> quote = quoteService.fetchLiveQuote(ticker);
                if (quote.isEmpty()) {
                    failed.add(ticker);
                    log.debug("Котировка {} не получена ни одним источником", ticker);
                    continue;
                }
                store(quote.get());
                refreshed.add(ticker);
            } catch (RuntimeException exception) {
                failed.add(ticker);
                log.warn("Не удалось обновить котировку {}: {}", ticker, exception.getMessage());
            }
        }
        log.info("Обновление котировок: {} ок, {} с ошибкой", refreshed.size(), failed.size());
        return new RefreshResultDto(refreshed, failed);
    }

    /** Объединение списка из конфигурации, таблицы отслеживаемых тикеров и позиций портфеля. */
    public List<String> trackedTickers() {
        Set<String> tickers = new LinkedHashSet<>();
        if (properties.tickers() != null) {
            properties.tickers().forEach(ticker -> tickers.add(ticker.trim().toUpperCase()));
        }
        tickers.addAll(trackedTickerRepository.findAll());
        tickers.addAll(positionTickerRepository.findTickers());
        return tickers.stream().filter(ticker -> !ticker.isBlank()).sorted().toList();
    }

    /** Добавить тикер в отслеживаемые (идемпотентно). */
    public void track(String ticker) {
        trackedTickerRepository.add(ticker);
    }

    /** Убрать тикер из отслеживаемых; тикеры из конфигурации остаются. */
    public void untrack(String ticker) {
        trackedTickerRepository.delete(ticker);
    }

    private void store(Quote quote) {
        Optional<Quote> previous = priceQuoteRepository.findLatest(quote.ticker());
        priceQuoteRepository.insert(quote);
        previous.filter(last -> last.price().signum() > 0)
                .ifPresent(last -> publishIfSignificant(last, quote));
    }

    private void publishIfSignificant(Quote previous, Quote current) {
        BigDecimal changePercent = current.price()
                .subtract(previous.price())
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.price(), 4, RoundingMode.HALF_UP);
        if (changePercent.abs().compareTo(
                BigDecimal.valueOf(properties.events().changeThresholdPercent())) < 0) {
            return;
        }
        priceEventPublisher.publish(new PriceChangedEvent(
                current.ticker(),
                previous.price(),
                current.price(),
                changePercent,
                current.currency(),
                current.quotedAt(),
                current.source()));
    }
}
