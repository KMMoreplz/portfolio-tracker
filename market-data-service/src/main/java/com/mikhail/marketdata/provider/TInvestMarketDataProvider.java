package com.mikhail.marketdata.provider;

import com.mikhail.common.marketdata.InstrumentInfo;
import com.mikhail.common.marketdata.MarketDataProvider;
import com.mikhail.common.marketdata.Quote;
import com.mikhail.common.marketdata.QuoteCandle;
import com.mikhail.marketdata.config.TInvestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Провайдер T-Invest API (Т-Банк) через официальный REST-прокси:
 * {@code POST /tinkoff.public.invest.api.contract.v1.<Service>/<Method>} с Bearer-токеном.
 * Провайдер создаётся только при непустом токене (см. {@link TInvestProperties#enabled()}).
 */
@Component
@Order(1)
public class TInvestMarketDataProvider implements MarketDataProvider {

    public static final String SOURCE_NAME = "t-invest";

    private static final Logger log = LoggerFactory.getLogger(TInvestMarketDataProvider.class);
    private static final String SERVICE_PREFIX = "tinkoff.public.invest.api.contract.v1.";
    private static final String CANDLE_INTERVAL_DAY = "CANDLE_INTERVAL_DAY";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TInvestProperties properties;
    private final Map<String, Optional<InstrumentInfo>> instrumentCache = new ConcurrentHashMap<>();
    private final Map<String, String> instrumentUids = new ConcurrentHashMap<>();

    public TInvestMarketDataProvider(
            RestClient tInvestRestClient,
            ObjectMapper objectMapper,
            TInvestProperties properties) {
        this.restClient = tInvestRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled();
    }

    @Override
    public Optional<Quote> findQuote(String ticker) {
        Optional<InstrumentInfo> instrument = findInstrument(ticker);
        if (instrument.isEmpty()) {
            return Optional.empty();
        }
        // GetLastPrices принимает figi, instrument_uid или ticker_класс; используем uid
        Optional<JsonNode> response = post("MarketDataService/GetLastPrices",
                "{\"instrumentId\":[\"" + instrumentUid(ticker) + "\"]}");
        if (response.isEmpty()) {
            return Optional.empty();
        }
        JsonNode lastPrices = response.get().get("lastPrices");
        if (lastPrices == null || !lastPrices.isArray() || lastPrices.isEmpty()) {
            log.debug("T-Invest: нет цен последней сделки для {}", ticker);
            return Optional.empty();
        }
        JsonNode lastPrice = lastPrices.get(0);
        BigDecimal price = quotation(lastPrice.get("price"));
        if (price == null) {
            return Optional.empty();
        }
        Instant quotedAt = lastPrice.hasNonNull("time")
                ? Instant.parse(lastPrice.get("time").asText())
                : Instant.now();
        return Optional.of(new Quote(
                ticker,
                price,
                instrument.get().currency(),
                quotedAt,
                SOURCE_NAME));
    }

    @Override
    public Optional<InstrumentInfo> findInstrument(String ticker) {
        Optional<InstrumentInfo> cached = instrumentCache.get(ticker);
        if (cached != null && cached.isPresent()) {
            return cached;
        }
        // отрицательный результат не кэшируем: стартовый прогон планировщика
        // мог упасть на неподготовленной заглушке/сети, кэш не должен это запомнить
        Optional<InstrumentInfo> loaded = loadInstrument(ticker);
        if (loaded.isPresent()) {
            instrumentCache.put(ticker, loaded);
        }
        return loaded;
    }

    /** UID инструмента для методов, принимающих instrumentId (GetDividends и т.п.). */
    public Optional<String> findInstrumentUid(String ticker) {
        if (findInstrument(ticker).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(instrumentUids.getOrDefault(ticker.toUpperCase(Locale.ROOT), ticker));
    }

    @Override
    public List<QuoteCandle> findDailyCandles(String ticker, LocalDate from, LocalDate to) {
        Optional<InstrumentInfo> instrument = findInstrument(ticker);
        if (instrument.isEmpty()) {
            return List.of();
        }
        String requestBody = "{\"instrumentId\":\"" + instrumentUid(ticker) + "\","
                + "\"from\":\"" + from.atStartOfDay(ZoneOffset.UTC) + "\","
                + "\"to\":\"" + to.plusDays(1).atStartOfDay(ZoneOffset.UTC) + "\","
                + "\"interval\":\"" + CANDLE_INTERVAL_DAY + "\"}";
        Optional<JsonNode> response = post("MarketDataService/GetCandles", requestBody);
        if (response.isEmpty()) {
            return List.of();
        }
        JsonNode candles = response.get().get("candles");
        if (candles == null || !candles.isArray()) {
            return List.of();
        }
        List<QuoteCandle> result = new ArrayList<>();
        for (JsonNode candle : candles) {
            BigDecimal open = quotation(candle.get("open"));
            BigDecimal high = quotation(candle.get("high"));
            BigDecimal low = quotation(candle.get("low"));
            BigDecimal close = quotation(candle.get("close"));
            if (open == null || high == null || low == null || close == null) {
                continue;
            }
            LocalDate date = Instant.parse(candle.get("time").asText()).atZone(ZoneOffset.UTC).toLocalDate();
            long volume = candle.hasNonNull("volume") ? candle.get("volume").asLong() : 0L;
            result.add(new QuoteCandle(date, open, high, low, close, volume));
        }
        return result;
    }

    private Optional<InstrumentInfo> loadInstrument(String ticker) {
        Optional<JsonNode> response = post("InstrumentsService/FindInstrument",
                "{\"query\":\"" + ticker + "\",\"apiTradeAvailableFlag\":true}");
        if (response.isEmpty()) {
            return Optional.empty();
        }
        JsonNode instruments = response.get().get("instruments");
        if (instruments == null || !instruments.isArray() || instruments.isEmpty()) {
            log.debug("T-Invest: инструмент {} не найден", ticker);
            return Optional.empty();
        }
        JsonNode match = null;
        for (JsonNode candidate : instruments) {
            if (ticker.equalsIgnoreCase(candidate.path("ticker").asText())) {
                match = candidate;
                break;
            }
        }
        JsonNode chosen = match != null ? match : instruments.get(0);
        String uid = chosen.path("uid").asText(null);
        if (uid == null || uid.isBlank()) {
            return Optional.empty();
        }
        instrumentUids.put(ticker.toUpperCase(Locale.ROOT), uid);
        String name = chosen.path("name").asText(null);
        String isin = chosen.path("isin").asText(null);
        int lot = chosen.has("lot") ? chosen.get("lot").asInt(1) : 1;
        return Optional.of(new InstrumentInfo(
                chosen.path("ticker").asText(ticker),
                name,
                isin,
                "RUB",
                Math.max(lot, 1),
                SOURCE_NAME));
    }

    private String instrumentUid(String ticker) {
        findInstrument(ticker);
        return instrumentUids.getOrDefault(ticker.toUpperCase(Locale.ROOT), ticker);
    }

    private Optional<JsonNode> post(String method, String requestBody) {
        try {
            String response = restClient.post()
                    .uri(SERVICE_PREFIX + method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return Optional.of(objectMapper.readTree(response));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (HttpClientErrorException exception) {
            log.warn("T-Invest API: {} {} — {}", exception.getStatusCode().value(), method,
                    exception.getResponseBodyAsString());
            return Optional.empty();
        }
    }

    private BigDecimal quotation(JsonNode quotation) {
        if (quotation == null || quotation.isNull()) {
            return null;
        }
        long units = quotation.hasNonNull("units")
                ? Long.parseLong(quotation.get("units").asText())
                : 0L;
        int nano = quotation.hasNonNull("nano") ? quotation.get("nano").asInt() : 0;
        return BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
    }
}
