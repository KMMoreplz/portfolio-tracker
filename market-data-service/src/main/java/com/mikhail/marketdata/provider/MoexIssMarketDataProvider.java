package com.mikhail.marketdata.provider;

import com.mikhail.common.marketdata.InstrumentInfo;
import com.mikhail.common.marketdata.MarketDataProvider;
import com.mikhail.common.marketdata.Quote;
import com.mikhail.common.marketdata.QuoteCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Провайдер публичного API Московской биржи (ISS): котировки, справка по бумаге
 * и исторические свечи без токена. Используется как основной источник, когда не задан
 * токен T-Invest, и как резервный, когда T-Invest API недоступен.
 */
@Component
@Order(2)
public class MoexIssMarketDataProvider implements MarketDataProvider {

    public static final String SOURCE_NAME = "moex-iss";
    private static final ZoneId MOEX_ZONE = ZoneId.of("Europe/Moscow");

    private static final Logger log = LoggerFactory.getLogger(MoexIssMarketDataProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Optional<InstrumentInfo>> instrumentCache = new ConcurrentHashMap<>();

    public MoexIssMarketDataProvider(RestClient moexRestClient, ObjectMapper objectMapper) {
        this.restClient = moexRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public Optional<Quote> findQuote(String ticker) {
        Optional<JsonNode> response = get("engines/stock/markets/shares/securities/" + ticker + ".json",
                Map.of("iss.only", "marketdata,securities"));
        if (response.isEmpty()) {
            return Optional.empty();
        }
        JsonNode marketData = response.get().get("marketdata");
        if (marketData == null) {
            return Optional.empty();
        }
        Map<String, Integer> columns = columns(marketData);
        List<JsonNode> rows = rows(marketData);
        if (rows.isEmpty()) {
            log.debug("MOEX ISS: нет данных marketdata для {}", ticker);
            return Optional.empty();
        }
        JsonNode row = rows.get(0);
        BigDecimal price = firstNonNullPrice(row, columns, "LAST", "CLOSEPRICE", "MARKETPRICE", "WAPRICE");
        if (price == null) {
            return Optional.empty();
        }
        String currency = currencyOf(response.get(), "RUB");
        Instant quotedAt = instantOf(row, columns, "SYSTIME").orElseGet(Instant::now);
        return Optional.of(new Quote(ticker, price, currency, quotedAt, SOURCE_NAME));
    }

    @Override
    public Optional<InstrumentInfo> findInstrument(String ticker) {
        Optional<InstrumentInfo> cached = instrumentCache.get(ticker);
        if (cached != null && cached.isPresent()) {
            return cached;
        }
        Optional<InstrumentInfo> loaded = loadInstrument(ticker);
        if (loaded.isPresent()) {
            instrumentCache.put(ticker, loaded);
        }
        return loaded;
    }

    @Override
    public List<QuoteCandle> findDailyCandles(String ticker, LocalDate from, LocalDate to) {
        Optional<JsonNode> response = get(
                "engines/stock/markets/shares/securities/" + ticker + "/candles.json",
                Map.of(
                        "iss.only", "candles",
                        "interval", "24",
                        "from", from.toString(),
                        "till", to.toString()));
        if (response.isEmpty()) {
            return List.of();
        }
        JsonNode candles = response.get().get("candles");
        if (candles == null) {
            return List.of();
        }
        Map<String, Integer> columns = columns(candles);
        List<QuoteCandle> result = new ArrayList<>();
        for (JsonNode row : rows(candles)) {
            LocalDate date = dateOf(row, columns, "begin");
            if (date == null) {
                continue;
            }
            result.add(new QuoteCandle(
                    date,
                    decimalAt(row, columns, "open"),
                    decimalAt(row, columns, "high"),
                    decimalAt(row, columns, "low"),
                    decimalAt(row, columns, "close"),
                    longAt(row, columns, "volume")));
        }
        return result;
    }

    private Optional<InstrumentInfo> loadInstrument(String ticker) {
        Optional<JsonNode> response = get("securities/" + ticker + ".json", Map.of());
        if (response.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> description = description(response.get());
        if (description.isEmpty()) {
            log.debug("MOEX ISS: инструмент {} не найден", ticker);
            return Optional.empty();
        }
        String name = description.getOrDefault("SHORTNAME", description.get("NAME"));
        int lot = 1;
        if (description.containsKey("LOTSIZE")) {
            try {
                lot = Integer.parseInt(description.get("LOTSIZE"));
            } catch (NumberFormatException ignored) {
                lot = 1;
            }
        }
        return Optional.of(new InstrumentInfo(
                description.getOrDefault("SECID", ticker),
                name,
                description.get("ISIN"),
                description.getOrDefault("CURRENCYID", "RUB"),
                Math.max(lot, 1),
                SOURCE_NAME));
    }

    private Optional<JsonNode> get(String path, Map<String, String> queryParams) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(uriBuilder -> {
                var builder = uriBuilder.path(path);
                queryParams.forEach(builder::queryParam);
                return builder.build();
            });
            String response = request.retrieve().body(String.class);
            return Optional.of(objectMapper.readTree(response));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (HttpClientErrorException exception) {
            log.warn("MOEX ISS: {} вернул {}", path, exception.getStatusCode().value());
            return Optional.empty();
        }
    }

    /** Описание инструмента приходит блоком description: rows вида [name, title, value, ...]. */
    private Map<String, String> description(JsonNode response) {
        Map<String, String> result = new HashMap<>();
        JsonNode description = response.get("description");
        if (description == null) {
            return result;
        }
        Map<String, Integer> columns = columns(description);
        Integer nameIndex = columns.get("name");
        Integer valueIndex = columns.get("value");
        if (nameIndex == null || valueIndex == null) {
            return result;
        }
        for (JsonNode row : rows(description)) {
            if (row.size() > Math.max(nameIndex, valueIndex)) {
                result.put(row.get(nameIndex).asText(), row.get(valueIndex).asText());
            }
        }
        return result;
    }

    private Map<String, Integer> columns(JsonNode block) {
        Map<String, Integer> result = new HashMap<>();
        JsonNode columnNode = block.get("columns");
        if (columnNode == null) {
            return result;
        }
        for (int index = 0; index < columnNode.size(); index++) {
            result.put(columnNode.get(index).asText(), index);
        }
        return result;
    }

    private List<JsonNode> rows(JsonNode block) {
        JsonNode data = block.get("data");
        if (data == null || !data.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        data.forEach(result::add);
        return result;
    }

    private BigDecimal firstNonNullPrice(JsonNode row, Map<String, Integer> columns, String... candidates) {
        for (String candidate : candidates) {
            BigDecimal value = decimalAt(row, columns, candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal decimalAt(JsonNode row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= row.size()) {
            return null;
        }
        JsonNode cell = row.get(index);
        if (cell == null || cell.isNull() || !cell.isNumber()) {
            return null;
        }
        return cell.decimalValue();
    }

    private long longAt(JsonNode row, Map<String, Integer> columns, String column) {
        BigDecimal value = decimalAt(row, columns, column);
        return value == null ? 0L : value.longValue();
    }

    private String currencyOf(JsonNode response, String fallback) {
        Map<String, String> description = description(response);
        return description.getOrDefault("CURRENCYID", fallback);
    }

    private Optional<Instant> instantOf(JsonNode row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= row.size()) {
            return Optional.empty();
        }
        JsonNode cell = row.get(index);
        if (cell == null || cell.isNull()) {
            return Optional.empty();
        }
        String text = cell.asText();
        try {
            LocalDateTime dateTime = LocalDateTime.parse(text.replace(' ', 'T'));
            return Optional.of(dateTime.atZone(MOEX_ZONE).toInstant());
        } catch (RuntimeException exception) {
            log.debug("Не удалось разобрать время '{}' из колонки {}", text, column);
            return Optional.empty();
        }
    }

    private LocalDate dateOf(JsonNode row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= row.size()) {
            return null;
        }
        JsonNode cell = row.get(index);
        if (cell == null || cell.isNull()) {
            return null;
        }
        String text = cell.asText();
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (RuntimeException exception) {
            log.debug("Не удалось разобрать дату '{}'", text);
            return null;
        }
    }
}
