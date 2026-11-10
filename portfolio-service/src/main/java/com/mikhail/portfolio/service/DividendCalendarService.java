package com.mikhail.portfolio.service;

import com.mikhail.common.domain.Currency;
import com.mikhail.common.domain.Money;
import com.mikhail.portfolio.error.NoExchangeRateException;
import com.mikhail.portfolio.error.PortfolioNotFoundException;
import com.mikhail.portfolio.marketdata.MarketDataClient;
import com.mikhail.portfolio.marketdata.MarketDataDividend;
import com.mikhail.portfolio.marketdata.MarketDataDividends;
import com.mikhail.portfolio.marketdata.MarketDataUnavailableException;
import com.mikhail.portfolio.model.Portfolio;
import com.mikhail.portfolio.model.Position;
import com.mikhail.portfolio.repository.PortfolioRepository;
import com.mikhail.portfolio.repository.PositionRepository;
import com.mikhail.portfolio.web.DividendCalendarDto;
import com.mikhail.portfolio.web.DividendCalendarItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Дивидендный календарь: предстоящие выплаты по бумагам текущих позиций.
 *
 * Источник будущих выплат — market-data-service (/api/dividends/{ticker});
 * размер выплаты считается как amountPerShare × количество бумаг в портфеле.
 * Если источник недоступен, ответ честно помечается marketDataAvailable=false,
 * а не выдаёт пустой список за «выплат нет».
 */
@Service
public class DividendCalendarService {

    private static final Logger log = LoggerFactory.getLogger(DividendCalendarService.class);
    private static final int MONEY_SCALE = 2;

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final MarketDataClient marketDataClient;
    private final InMemoryExchangeRateProvider exchangeRateProvider;

    public DividendCalendarService(PortfolioRepository portfolioRepository,
                                   PositionRepository positionRepository,
                                   MarketDataClient marketDataClient,
                                   InMemoryExchangeRateProvider exchangeRateProvider) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.marketDataClient = marketDataClient;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    @Transactional(readOnly = true)
    public DividendCalendarDto calendar(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        Currency base = requireCurrency(portfolio.getBaseCurrency());

        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);
        if (positions.isEmpty()) {
            return new DividendCalendarDto(portfolioId, base, true, List.of());
        }

        List<DividendCalendarItemDto> items = new ArrayList<>();
        for (Position position : positions) {
            Optional<MarketDataDividends> dividends;
            try {
                dividends = marketDataClient.dividends(position.getTicker());
            } catch (MarketDataUnavailableException e) {
                log.warn("Дивидендный календарь: источник недоступен, отдаём пустой список: {}",
                        e.getMessage());
                return new DividendCalendarDto(portfolioId, base, false, List.of());
            }
            dividends.ifPresent(payload -> collectUpcoming(payload, position, base, items));
        }

        items.sort(Comparator
                .comparing(DividendCalendarItemDto::paymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DividendCalendarItemDto::ticker));
        return new DividendCalendarDto(portfolioId, base, true, items);
    }

    private void collectUpcoming(MarketDataDividends payload, Position position,
                                 Currency base, List<DividendCalendarItemDto> items) {
        if (payload.upcoming() == null) {
            return;
        }
        for (MarketDataDividend dividend : payload.upcoming()) {
            if (dividend.paymentDate() == null || dividend.amountPerShare() == null) {
                continue;
            }
            Currency payoutCurrency = resolveCurrency(dividend.currency(), position.getCurrency());
            BigDecimal payout = dividend.amountPerShare()
                    .multiply(position.getQuantity())
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal payoutInBase = null;
            if (payoutCurrency != null) {
                payoutInBase = exchangeRateProvider
                        .convert(Money.of(payout, payoutCurrency), base)
                        .amount()
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
            items.add(new DividendCalendarItemDto(
                    position.getTicker(),
                    position.getInstrumentName(),
                    position.getQuantity(),
                    dividend.exDate(),
                    dividend.paymentDate(),
                    dividend.amountPerShare(),
                    payoutCurrency == null ? null : payoutCurrency.getCode(),
                    payout,
                    payoutInBase,
                    dividend.source() == null ? payload.source() : dividend.source()));
        }
    }

    private Currency resolveCurrency(String payoutCurrencyCode, String positionCurrencyCode) {
        Currency currency = payoutCurrencyCode == null ? null : Currency.fromCode(payoutCurrencyCode);
        if (currency == null && positionCurrencyCode != null) {
            currency = Currency.fromCode(positionCurrencyCode);
        }
        return currency;
    }

    private Currency requireCurrency(String code) {
        Currency currency = Currency.fromCode(code);
        if (currency == null) {
            throw new NoExchangeRateException(code);
        }
        return currency;
    }
}
