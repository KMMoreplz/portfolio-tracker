package com.mikhail.marketdata.service;

import com.mikhail.common.marketdata.MarketDataProvider;
import com.mikhail.common.marketdata.Quote;
import com.mikhail.common.marketdata.QuoteCandle;
import com.mikhail.marketdata.repository.PriceQuoteRepository;
import com.mikhail.marketdata.resilience.ExternalSourceUnavailableException;
import com.mikhail.marketdata.resilience.ResilienceExecutor;
import com.mikhail.marketdata.web.dto.QuoteCandleDto;
import com.mikhail.marketdata.web.dto.QuoteDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Чтение котировок: сначала внешние источники (в порядке приоритета, каждый —
 * под ретраями и circuit breaker'ом), при недоступности всех — fallback на
 * последние сохранённые котировки с флагом {@code cached}.
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final List<MarketDataProvider> providers;
    private final ResilienceExecutor resilience;
    private final PriceQuoteRepository priceQuoteRepository;

    public QuoteService(
            List<MarketDataProvider> providers,
            ResilienceExecutor resilience,
            PriceQuoteRepository priceQuoteRepository) {
        this.providers = providers;
        this.resilience = resilience;
        this.priceQuoteRepository = priceQuoteRepository;
    }

    /** Живая котировка из внешних источников без fallback на БД. */
    public Optional<Quote> fetchLiveQuote(String ticker) {
        for (MarketDataProvider provider : providers) {
            if (!provider.isEnabled()) {
                continue;
            }
            try {
                Optional<Quote> quote = resilience.execute(
                        provider.sourceName(), () -> provider.findQuote(ticker));
                if (quote.isPresent()) {
                    return quote;
                }
            } catch (ExternalSourceUnavailableException exception) {
                log.warn("Источник '{}' недоступен для {}: {}",
                        provider.sourceName(), ticker, exception.getCause() != null
                                ? exception.getCause().getMessage() : exception.getMessage());
            }
        }
        return Optional.empty();
    }

    public Optional<QuoteDto> getQuote(String ticker) {
        Optional<Quote> live = fetchLiveQuote(ticker);
        if (live.isPresent()) {
            return Optional.of(QuoteDto.live(live.get()));
        }
        return priceQuoteRepository.findLatest(ticker).map(QuoteDto::cached);
    }

    public List<QuoteDto> getQuotes(Collection<String> tickers) {
        List<QuoteDto> result = new ArrayList<>();
        for (String ticker : tickers) {
            getQuote(ticker).ifPresent(result::add);
        }
        return result;
    }

    public List<QuoteCandleDto> getHistory(String ticker, LocalDate from, LocalDate to) {
        ExternalSourceUnavailableException lastFailure = null;
        for (MarketDataProvider provider : providers) {
            if (!provider.isEnabled()) {
                continue;
            }
            try {
                List<QuoteCandle> candles = resilience.execute(
                        provider.sourceName(), () -> provider.findDailyCandles(ticker, from, to));
                if (!candles.isEmpty()) {
                    return candles.stream().map(QuoteCandleDto::from).toList();
                }
            } catch (ExternalSourceUnavailableException exception) {
                lastFailure = exception;
                log.warn("Источник '{}' недоступен для истории {}: {}",
                        provider.sourceName(), ticker, exception.getMessage());
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return List.of();
    }
}
