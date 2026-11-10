package com.mikhail.marketdata.service;

import com.mikhail.marketdata.config.MarketDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Плановое обновление котировок: раз в {@code market-data.refresh-interval-ms}
 * (по умолчанию 5 минут) и только внутри окна торгов, если оно включено
 * ({@code market-data.trading-hours.enforced}).
 */
@Component
public class QuoteRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuoteRefreshScheduler.class);

    private final MarketDataProperties properties;
    private final TradingHours tradingHours;
    private final QuoteRefreshService quoteRefreshService;

    public QuoteRefreshScheduler(
            MarketDataProperties properties,
            TradingHours tradingHours,
            QuoteRefreshService quoteRefreshService) {
        this.properties = properties;
        this.tradingHours = tradingHours;
        this.quoteRefreshService = quoteRefreshService;
    }

    @Scheduled(fixedDelayString = "${market-data.refresh-interval-ms:300000}",
            initialDelayString = "${market-data.refresh-interval-ms:300000}")
    public void refreshQuotes() {
        if (properties.tradingHours().enforced() && !tradingHours.isOpen(Instant.now())) {
            log.debug("Вне окна торгов — обновление котировок пропущено");
            return;
        }
        quoteRefreshService.refreshAll();
    }
}
