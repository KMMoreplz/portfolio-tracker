package com.mikhail.portfolio.service;

import com.mikhail.common.domain.Currency;
import com.mikhail.common.domain.Money;
import com.mikhail.common.marketdata.MarketDataProvider;
import com.mikhail.common.marketdata.Quote;
import com.mikhail.portfolio.error.NoExchangeRateException;
import com.mikhail.portfolio.error.PortfolioNotFoundException;
import com.mikhail.portfolio.model.DividendTotal;
import com.mikhail.portfolio.model.Portfolio;
import com.mikhail.portfolio.model.Position;
import com.mikhail.portfolio.repository.DividendEventRepository;
import com.mikhail.portfolio.repository.PortfolioRepository;
import com.mikhail.portfolio.repository.PositionRepository;
import com.mikhail.portfolio.repository.PriceQuoteRepository;
import com.mikhail.portfolio.web.MetricsDto;
import com.mikhail.portfolio.web.PositionMetricsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Расчёт метрик портфеля.
 *
 * Источник цены позиции: последняя сохранённая котировка (price_quotes) либо
 * {@link MarketDataProvider}; если котировки нет, позиция честно считается
 * по средней цене покупки, и поле priceSource сообщает об этом клиенту.
 * Валюты пересчитываются в базовую валюту портфеля, по неизвестной валюте
 * возвращается понятная ошибка, а не молчаливая подмена.
 */
@Service
public class MetricsService {

    private static final String AVERAGE_PRICE_SOURCE = "average-price";
    private static final int MONEY_SCALE = 2;
    private static final int PRICE_SCALE = 8;

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final DividendEventRepository dividendEventRepository;
    private final MarketDataProvider marketDataProvider;
    private final InMemoryExchangeRateProvider exchangeRateProvider;

    public MetricsService(PortfolioRepository portfolioRepository,
                          PositionRepository positionRepository,
                          PriceQuoteRepository priceQuoteRepository,
                          DividendEventRepository dividendEventRepository,
                          MarketDataProvider marketDataProvider,
                          InMemoryExchangeRateProvider exchangeRateProvider) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.dividendEventRepository = dividendEventRepository;
        this.marketDataProvider = marketDataProvider;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    /** readOnly выключено: использованные котировки сохраняются в price_quotes как аудит оценки. */
    @Transactional
    public MetricsDto metrics(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        Currency base = requireCurrency(portfolio.getBaseCurrency());

        List<Evaluated> evaluated = positionRepository.findByPortfolioId(portfolioId).stream()
                .map(position -> evaluate(position, base))
                .sorted(Comparator.comparing(Evaluated::valueInBase).reversed()
                        .thenComparing(Evaluated::ticker))
                .toList();

        BigDecimal totalValue = sum(evaluated, Evaluated::valueInBase);
        BigDecimal totalCost = sum(evaluated, Evaluated::costInBase);
        BigDecimal profit = totalValue.subtract(totalCost);
        BigDecimal profitPercent = percent(profit, totalCost);
        BigDecimal dividends = dividendsInBase(portfolioId, base);

        List<PositionMetricsDto> positions = evaluated.stream()
                .map(item -> item.toDto(totalValue))
                .toList();

        return new MetricsDto(
                portfolioId,
                portfolio.getName(),
                base,
                totalValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                totalCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                profit.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                profitPercent,
                dividends.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                positions,
                OffsetDateTime.now());
    }

    private Evaluated evaluate(Position position, Currency base) {
        Currency positionCurrency = requireCurrency(position.getCurrency());
        Optional<Quote> quote = priceQuoteRepository.findLatestByTicker(position.getTicker())
                .or(() -> marketDataProvider.findQuote(position.getTicker()));

        BigDecimal currentPrice;
        String priceSource;
        OffsetDateTime priceAsOf = null;
        if (quote.isPresent() && positionCurrency.getCode().equals(quote.get().currency())) {
            Quote found = quote.get();
            currentPrice = found.price();
            priceSource = found.source();
            priceAsOf = OffsetDateTime.ofInstant(found.quotedAt(), ZoneOffset.UTC);
            // аудит: в price_quotes остаётся котировка, по которой выполнена оценка
            priceQuoteRepository.insertIfAbsent(found);
        } else {
            currentPrice = position.getAveragePrice();
            priceSource = AVERAGE_PRICE_SOURCE;
        }

        BigDecimal value = position.getQuantity().multiply(currentPrice)
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal cost = position.getQuantity().multiply(position.getAveragePrice())
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal valueInBase = exchangeRateProvider
                .convert(Money.of(value, positionCurrency), base).amount();
        BigDecimal costInBase = exchangeRateProvider
                .convert(Money.of(cost, positionCurrency), base).amount();

        return new Evaluated(position, positionCurrency, currentPrice, priceSource, priceAsOf,
                value, cost, valueInBase, costInBase);
    }

    private BigDecimal dividendsInBase(Long portfolioId, Currency base) {
        BigDecimal total = BigDecimal.ZERO;
        for (DividendTotal dividend : dividendEventRepository.totalsByPortfolioId(portfolioId)) {
            Currency currency = requireCurrency(dividend.currency());
            BigDecimal converted = exchangeRateProvider
                    .convert(Money.of(dividend.amount(), currency), base).amount();
            total = total.add(converted);
        }
        return total;
    }

    private Currency requireCurrency(String code) {
        Currency currency = Currency.fromCode(code);
        if (currency == null) {
            throw new NoExchangeRateException(code);
        }
        return currency;
    }

    private BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(whole, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<Evaluated> items, java.util.function.Function<Evaluated, BigDecimal> field) {
        return items.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Позиция с оценкой в валюте позиции и в базовой валюте портфеля. */
    private record Evaluated(Position position,
                             Currency currency,
                             BigDecimal currentPrice,
                             String priceSource,
                             OffsetDateTime priceAsOf,
                             BigDecimal value,
                             BigDecimal cost,
                             BigDecimal valueInBase,
                             BigDecimal costInBase) {

        String ticker() {
            return position.getTicker();
        }

        PositionMetricsDto toDto(BigDecimal totalValue) {
            BigDecimal profit = valueInBase.subtract(costInBase);
            BigDecimal profitPercent = costInBase.signum() == 0
                    ? null
                    : percentOf(profit, costInBase);
            BigDecimal weightPercent = totalValue.signum() == 0
                    ? BigDecimal.ZERO
                    : valueInBase.multiply(BigDecimal.valueOf(100))
                            .divide(totalValue, MONEY_SCALE, RoundingMode.HALF_UP);
            return new PositionMetricsDto(
                    position.getTicker(),
                    position.getInstrumentName(),
                    position.getQuantity(),
                    position.getAveragePrice(),
                    currentPrice,
                    priceSource,
                    priceAsOf,
                    currency,
                    value.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    cost.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    profit.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    profitPercent,
                    weightPercent,
                    valueInBase.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }

        private BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
            return part.multiply(BigDecimal.valueOf(100))
                    .divide(whole, MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }
}
