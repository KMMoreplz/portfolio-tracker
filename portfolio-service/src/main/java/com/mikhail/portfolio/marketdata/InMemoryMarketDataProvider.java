package com.mikhail.portfolio.marketdata;

import com.mikhail.common.marketdata.InstrumentInfo;
import com.mikhail.common.marketdata.MarketDataProvider;
import com.mikhail.common.marketdata.Quote;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Локальная заглушка рыночных данных для разработки и тестов: несколько известных
 * тикеров с фиксированной ценой и справкой по инструменту. Тикеры вне списка
 * считаются неизвестными — метрики в этом случае честно считают позицию
 * по средней цене покупки (поле {@code priceSource} показывает источник цены).
 *
 * Настоящий источник (T-Invest, MOEX ISS) реализуется в market-data-service
 * отдельной задачей — потребитель зависит только от интерфейса
 * {@link MarketDataProvider}, замена заглушки не трогает остальной код.
 */
@Component
public class InMemoryMarketDataProvider implements MarketDataProvider {

    /** Имя источника — попадает в поле source котировки и в price_quotes. */
    public static final String SOURCE = "in-memory-stub";

    /** Момент, «на который» заглушка отдаёт данные (в момент старта приложения). */
    private final Instant quotedAt = Instant.now();

    private final Map<String, Quote> quotes;
    private final Map<String, InstrumentInfo> instruments;

    public InMemoryMarketDataProvider() {
        this.quotes = Map.ofEntries(
                Map.entry("SBER", quote("SBER", "310.50", "RUB")),
                Map.entry("GAZP", quote("GAZP", "160.25", "RUB")),
                Map.entry("LKOH", quote("LKOH", "6500.00", "RUB")),
                Map.entry("MOEX", quote("MOEX", "195.30", "RUB")),
                Map.entry("VTBR", quote("VTBR", "98.40", "RUB")),
                Map.entry("AAPL", quote("AAPL", "235.60", "USD")),
                Map.entry("MSFT", quote("MSFT", "425.10", "USD")));
        this.instruments = Map.ofEntries(
                Map.entry("SBER", instrument("SBER", "Сбербанк", "RU0009029540", "RUB", 10)),
                Map.entry("GAZP", instrument("GAZP", "Газпром", "RU0007661625", "RUB", 10)),
                Map.entry("LKOH", instrument("LKOH", "Лукойл", "RU0009024277", "RUB", 1)),
                Map.entry("MOEX", instrument("MOEX", "Московская биржа", "RU000A0JNTP8", "RUB", 10)),
                Map.entry("VTBR", instrument("VTBR", "ВТБ", "RU000A0JP5V6", "RUB", 100)),
                Map.entry("AAPL", instrument("AAPL", "Apple Inc", "US0378331005", "USD", 1)),
                Map.entry("MSFT", instrument("MSFT", "Microsoft Corp", "US5949181045", "USD", 1)));
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public Optional<Quote> findQuote(String ticker) {
        return Optional.ofNullable(quotes.get(ticker));
    }

    @Override
    public Optional<InstrumentInfo> findInstrument(String ticker) {
        return Optional.ofNullable(instruments.get(ticker));
    }

    private Quote quote(String ticker, String price, String currency) {
        return new Quote(ticker, new BigDecimal(price), currency, quotedAt, SOURCE);
    }

    private InstrumentInfo instrument(String ticker, String name, String isin, String currency, int lotSize) {
        return new InstrumentInfo(ticker, name, isin, currency, lotSize, SOURCE);
    }
}
