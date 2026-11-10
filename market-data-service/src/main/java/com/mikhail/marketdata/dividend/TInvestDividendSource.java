package com.mikhail.marketdata.dividend;

import com.mikhail.common.marketdata.Dividend;
import com.mikhail.marketdata.config.TInvestProperties;
import com.mikhail.marketdata.provider.TInvestMarketDataProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Дивиденды из T-Invest API: {@code InstrumentsService/GetDividends} по uid
 * инструмента за период (по умолчанию с 2015 года до года вперёд).
 */
@Component
public class TInvestDividendSource implements DividendSource {

    public static final String SOURCE_NAME = "t-invest";

    private static final String SERVICE_PREFIX = "tinkoff.public.invest.api.contract.v1.";
    private static final LocalDate FROM = LocalDate.of(2015, 1, 1);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TInvestMarketDataProvider marketDataProvider;
    private final TInvestProperties properties;

    public TInvestDividendSource(
            RestClient tInvestRestClient,
            ObjectMapper objectMapper,
            TInvestMarketDataProvider marketDataProvider,
            TInvestProperties properties) {
        this.restClient = tInvestRestClient;
        this.objectMapper = objectMapper;
        this.marketDataProvider = marketDataProvider;
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
    public List<Dividend> findDividends(String ticker) {
        Optional<String> uid = marketDataProvider.findInstrumentUid(ticker);
        if (uid.isEmpty()) {
            return List.of();
        }
        String requestBody = "{\"instrumentId\":\"" + uid.get() + "\","
                + "\"from\":\"" + FROM.atStartOfDay(ZoneOffset.UTC) + "\","
                + "\"to\":\"" + LocalDate.now(ZoneOffset.UTC).plusYears(1).atStartOfDay(ZoneOffset.UTC) + "\"}";
        String response = restClient.post()
                .uri(SERVICE_PREFIX + "InstrumentsService/GetDividends")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        JsonNode dividends = objectMapper.readTree(response).get("dividends");
        if (dividends == null || !dividends.isArray()) {
            return List.of();
        }
        List<Dividend> result = new ArrayList<>();
        for (JsonNode node : dividends) {
            result.add(new Dividend(
                    ticker,
                    localDate(node, "declaredDate"),
                    localDate(node, "lastBuyDate"),
                    localDate(node, "recordDate"),
                    localDate(node, "paymentDate"),
                    money(node.get("dividendNet")),
                    currency(node),
                    text(node, "regularity"),
                    text(node, "dividendType"),
                    SOURCE_NAME));
        }
        return result;
    }

    private LocalDate localDate(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        String text = node.get(field).asText();
        try {
            return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (RuntimeException exception) {
            // формат без секунд/зоны: берём дату из начала строки
            return LocalDate.parse(text.substring(0, 10));
        }
    }

    private BigDecimal money(JsonNode money) {
        if (money == null || money.isNull()) {
            return null;
        }
        long units = money.hasNonNull("units") ? Long.parseLong(money.get("units").asText()) : 0L;
        int nano = money.hasNonNull("nano") ? money.get("nano").asInt() : 0;
        return BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
    }

    private String currency(JsonNode node) {
        JsonNode dividendNet = node.get("dividendNet");
        if (dividendNet != null && dividendNet.hasNonNull("currency")) {
            return dividendNet.get("currency").asText();
        }
        return "RUB";
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText(null) : null;
    }
}
