package com.mikhail.marketdata.service;

import com.mikhail.marketdata.config.MarketDataProperties;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Окно торгов биржи (по умолчанию 10:00–18:35 по Москве, пн–пт):
 * вне окна планировщик не опрашивает источники.
 */
@Component
public class TradingHours {

    private final MarketDataProperties properties;

    public TradingHours(MarketDataProperties properties) {
        this.properties = properties;
    }

    public boolean isOpen(Instant instant) {
        MarketDataProperties.TradingHours window = properties.tradingHours();
        ZonedDateTime dateTime = instant.atZone(ZoneId.of(window.zone()));
        if (dateTime.getDayOfWeek() == DayOfWeek.SATURDAY || dateTime.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        LocalTime start = LocalTime.parse(window.start());
        LocalTime end = LocalTime.parse(window.end());
        return !time.isBefore(start) && !time.isAfter(end);
    }
}
