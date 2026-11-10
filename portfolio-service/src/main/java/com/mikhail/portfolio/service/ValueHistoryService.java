package com.mikhail.portfolio.service;

import com.mikhail.common.domain.Currency;
import com.mikhail.common.domain.Money;
import com.mikhail.common.domain.Transaction;
import com.mikhail.common.domain.TransactionType;
import com.mikhail.common.marketdata.Quote;
import com.mikhail.portfolio.error.NoExchangeRateException;
import com.mikhail.portfolio.error.PortfolioNotFoundException;
import com.mikhail.portfolio.marketdata.MarketDataCandle;
import com.mikhail.portfolio.marketdata.MarketDataClient;
import com.mikhail.portfolio.marketdata.MarketDataUnavailableException;
import com.mikhail.portfolio.model.Portfolio;
import com.mikhail.portfolio.model.Position;
import com.mikhail.portfolio.repository.PortfolioRepository;
import com.mikhail.portfolio.repository.PositionRepository;
import com.mikhail.portfolio.repository.PriceQuoteRepository;
import com.mikhail.portfolio.repository.TransactionRepository;
import com.mikhail.portfolio.web.ValueHistoryDto;
import com.mikhail.portfolio.web.ValueHistoryPointDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * История стоимости портфеля по дням — данные для графика на дашборде.
 *
 * Для каждого дня: количество бумаг воспроизводится из журнала операций
 * (покупки прибавляют, продажи вычитают), цена бумаги берётся в порядке
 * приоритета — дневное закрытие из market-data-service, затем последняя
 * сохранённая котировка (price_quotes), затем средняя цена покупки.
 * Вложенные деньги считаются как чистый денежный поток операций.
 * История курсов валют не хранится, поэтому пересчёт в базовую валюту
 * выполняется по текущим курсам заглушки — для графика это честная оговорка,
 * зафиксированная в README.
 */
@Service
public class ValueHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ValueHistoryService.class);
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;
    private static final int MONEY_SCALE = 2;
    private static final int PRICE_SCALE = 8;

    /** Цены из истории котировок снаружи. */
    static final String SOURCE_MARKET_DATA = "market-data";
    /** Цены из локальных котировок и средних цен покупки. */
    static final String SOURCE_LOCAL_FALLBACK = "local-fallback";
    /** Операций нет — точек нет. */
    static final String SOURCE_NO_DATA = "no-data";

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final PositionRepository positionRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final MarketDataClient marketDataClient;
    private final InMemoryExchangeRateProvider exchangeRateProvider;

    public ValueHistoryService(PortfolioRepository portfolioRepository,
                               TransactionRepository transactionRepository,
                               PositionRepository positionRepository,
                               PriceQuoteRepository priceQuoteRepository,
                               MarketDataClient marketDataClient,
                               InMemoryExchangeRateProvider exchangeRateProvider) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.positionRepository = positionRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.marketDataClient = marketDataClient;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    @Transactional(readOnly = true)
    public ValueHistoryDto history(Long portfolioId, Integer daysRequested) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        Currency base = requireCurrency(portfolio.getBaseCurrency());

        int days = daysRequested == null ? DEFAULT_DAYS
                : Math.max(1, Math.min(MAX_DAYS, daysRequested));

        List<Transaction> transactions = new ArrayList<>(
                transactionRepository.findByPortfolioId(portfolioId, null));
        if (transactions.isEmpty()) {
            return new ValueHistoryDto(portfolioId, base, SOURCE_NO_DATA, List.of());
        }
        transactions.sort(Comparator.comparing(Transaction::executedAt));

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1L);
        Set<String> tickers = new LinkedHashSet<>();
        transactions.forEach(transaction -> tickers.add(transaction.ticker()));

        boolean marketDataReachable = true;
        Map<String, NavigableMap<LocalDate, BigDecimal>> closesByTicker = new HashMap<>();
        for (String ticker : tickers) {
            try {
                Optional<List<MarketDataCandle>> history = marketDataClient.history(ticker, start, today);
                history.ifPresent(candles -> {
                    NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
                    for (MarketDataCandle candle : candles) {
                        if (candle.close() != null) {
                            closes.put(candle.date(), candle.close());
                        }
                    }
                    if (!closes.isEmpty()) {
                        closesByTicker.put(ticker, closes);
                    }
                });
            } catch (MarketDataUnavailableException e) {
                log.warn("График стоимости: market-data недоступен, берём сохранённые котировки: {}",
                        e.getMessage());
                marketDataReachable = false;
                break;
            }
        }

        Map<String, Position> positionsByTicker = new HashMap<>();
        positionRepository.findByPortfolioId(portfolioId)
                .forEach(position -> positionsByTicker.put(position.getTicker(), position));

        Map<String, String> currencyByTicker = new HashMap<>();
        transactions.forEach(transaction ->
                currencyByTicker.putIfAbsent(transaction.ticker(), transaction.currency().getCode()));
        positionsByTicker.forEach((ticker, position) ->
                currencyByTicker.put(ticker, position.getCurrency()));

        Map<String, NavigableMap<LocalDate, BigDecimal>> savedQuotesByTicker = new HashMap<>();
        for (String ticker : tickers) {
            NavigableMap<LocalDate, BigDecimal> byDate = new TreeMap<>();
            for (Quote quote : priceQuoteRepository.findByTicker(ticker)) {
                LocalDate date = OffsetDateTime.ofInstant(quote.quotedAt(), ZoneOffset.UTC).toLocalDate();
                byDate.put(date, quote.price());
            }
            if (!byDate.isEmpty()) {
                savedQuotesByTicker.put(ticker, byDate);
            }
        }

        List<ValueHistoryPointDto> points = buildPoints(start, today, transactions,
                positionsByTicker, currencyByTicker, closesByTicker, savedQuotesByTicker, base);

        String priceSource = marketDataReachable && !closesByTicker.isEmpty()
                ? SOURCE_MARKET_DATA
                : SOURCE_LOCAL_FALLBACK;
        return new ValueHistoryDto(portfolioId, base, priceSource, points);
    }

    private List<ValueHistoryPointDto> buildPoints(LocalDate start, LocalDate today,
                                                   List<Transaction> transactions,
                                                   Map<String, Position> positionsByTicker,
                                                   Map<String, String> currencyByTicker,
                                                   Map<String, NavigableMap<LocalDate, BigDecimal>> closesByTicker,
                                                   Map<String, NavigableMap<LocalDate, BigDecimal>> savedQuotesByTicker,
                                                   Currency base) {
        List<ValueHistoryPointDto> points = new ArrayList<>();
        Map<String, BigDecimal> quantities = new HashMap<>();
        BigDecimal cashFlow = BigDecimal.ZERO;
        int transactionIndex = 0;

        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            while (transactionIndex < transactions.size()
                    && transactions.get(transactionIndex).executedAt().toLocalDate().isBefore(date.plusDays(1))) {
                Transaction transaction = transactions.get(transactionIndex++);
                BigDecimal gross = transaction.grossAmount();
                BigDecimal commission = transaction.commission();
                switch (transaction.type()) {
                    case BUY -> {
                        cashFlow = cashFlow.subtract(gross).subtract(commission);
                        quantities.merge(transaction.ticker(), transaction.quantity(), BigDecimal::add);
                    }
                    case SELL -> {
                        cashFlow = cashFlow.add(gross).subtract(commission);
                        quantities.merge(transaction.ticker(), transaction.quantity().negate(), BigDecimal::add);
                    }
                    case DIVIDEND -> cashFlow = cashFlow.add(gross);
                }
            }

            BigDecimal totalValue = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> entry : quantities.entrySet()) {
                BigDecimal quantity = entry.getValue();
                if (quantity.signum() == 0) {
                    continue;
                }
                String ticker = entry.getKey();
                BigDecimal price = resolvePrice(ticker, date, positionsByTicker,
                        closesByTicker, savedQuotesByTicker);
                if (price == null) {
                    continue;
                }
                String currencyCode = currencyByTicker.get(ticker);
                Currency currency = currencyCode == null ? null : Currency.fromCode(currencyCode);
                if (currency == null) {
                    continue;
                }
                BigDecimal value = quantity.multiply(price).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
                totalValue = totalValue.add(
                        exchangeRateProvider.convert(Money.of(value, currency), base).amount());
            }

            points.add(new ValueHistoryPointDto(
                    date,
                    totalValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    cashFlow.negate().setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
        }
        return points;
    }

    /** Цена бумаги на дату: закрытие market-data → сохранённая котировка → средняя цена покупки. */
    private BigDecimal resolvePrice(String ticker, LocalDate date,
                                    Map<String, Position> positionsByTicker,
                                    Map<String, NavigableMap<LocalDate, BigDecimal>> closesByTicker,
                                    Map<String, NavigableMap<LocalDate, BigDecimal>> savedQuotesByTicker) {
        NavigableMap<LocalDate, BigDecimal> closes = closesByTicker.get(ticker);
        if (closes != null) {
            Map.Entry<LocalDate, BigDecimal> close = closes.floorEntry(date);
            if (close != null) {
                return close.getValue();
            }
        }
        NavigableMap<LocalDate, BigDecimal> saved = savedQuotesByTicker.get(ticker);
        if (saved != null) {
            Map.Entry<LocalDate, BigDecimal> quote = saved.floorEntry(date);
            if (quote != null) {
                return quote.getValue();
            }
        }
        Position position = positionsByTicker.get(ticker);
        return position == null ? null : position.getAveragePrice();
    }

    private Currency requireCurrency(String code) {
        Currency currency = Currency.fromCode(code);
        if (currency == null) {
            throw new NoExchangeRateException(code);
        }
        return currency;
    }
}
