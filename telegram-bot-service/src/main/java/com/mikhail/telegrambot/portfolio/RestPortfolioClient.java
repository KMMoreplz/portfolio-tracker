package com.mikhail.telegrambot.portfolio;

import com.mikhail.telegrambot.error.PortfolioServiceUnavailableException;
import com.mikhail.telegrambot.error.TokenExpiredException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST-клиент portfolio-service: привязка по коду, метрики портфелей,
 * дивидендный календарь и настройки пользователя. Тела ответов читаются
 * как строки и разбираются через {@link JsonNode} — так формат ответа
 * ядра не связывает бота с его DTO.
 */
@Component
public class RestPortfolioClient implements PortfolioClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestPortfolioClient(
            @Value("${portfolio-service.base-url:http://localhost:8080}") String baseUrl,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LinkResult> link(String code) {
        try {
            String body = restClient.post()
                    .uri("/api/telegram/link")
                    .body(Map.of("code", code))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body);
            return Optional.of(new LinkResult(
                    node.path("userId").asLong(),
                    node.path("email").asText(),
                    node.path("token").asText(),
                    node.path("expiresInSeconds").asLong(),
                    nullableDecimal(node, "priceAlertThreshold")));
        } catch (HttpClientErrorException.Unauthorized e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new PortfolioServiceUnavailableException("portfolio-service недоступен", e);
        } catch (RuntimeException e) {
            throw new PortfolioServiceUnavailableException(
                    "portfolio-service вернул некорректный ответ", e);
        }
    }

    @Override
    public List<PortfolioInfo> listPortfolios(String token) {
        JsonNode array = parseArray(get("/api/portfolios", token), "/api/portfolios");
        List<PortfolioInfo> portfolios = new ArrayList<>();
        for (JsonNode node : array) {
            portfolios.add(new PortfolioInfo(
                    node.path("id").asLong(),
                    node.path("name").asText(),
                    node.path("baseCurrency").asText()));
        }
        return portfolios;
    }

    @Override
    public Optional<PortfolioMetrics> metrics(String token, long portfolioId) {
        JsonNode node = objectMapper.readTree(
                get("/api/portfolios/" + portfolioId + "/metrics", token));
        List<PositionValue> positions = new ArrayList<>();
        for (JsonNode position : node.path("positions")) {
            positions.add(new PositionValue(
                    position.path("ticker").asText(),
                    position.path("instrumentName").asText(null),
                    nullableDecimal(position, "valueInBase"),
                    position.path("currency").asText(null),
                    nullableDecimal(position, "profitPercent"),
                    nullableDecimal(position, "weightPercent")));
        }
        return Optional.of(new PortfolioMetrics(
                node.path("portfolioId").asLong(),
                node.path("portfolioName").asText(),
                node.path("baseCurrency").asText(),
                nullableDecimal(node, "totalValue"),
                nullableDecimal(node, "totalCost"),
                nullableDecimal(node, "profit"),
                nullableDecimal(node, "profitPercent"),
                nullableDecimal(node, "dividendsReceived"),
                positions));
    }

    @Override
    public DividendCalendar dividendCalendar(String token, long portfolioId) {
        JsonNode node = objectMapper.readTree(
                get("/api/portfolios/" + portfolioId + "/dividend-calendar", token));
        List<DividendItem> items = new ArrayList<>();
        for (JsonNode item : node.path("items")) {
            items.add(new DividendItem(
                    item.path("ticker").asText(),
                    item.path("instrumentName").asText(null),
                    localDate(item, "paymentDate"),
                    localDate(item, "exDate"),
                    nullableDecimal(item, "amountPerShare"),
                    item.path("currency").asText(null),
                    nullableDecimal(item, "payout"),
                    nullableDecimal(item, "payoutInBase")));
        }
        return new DividendCalendar(
                node.path("portfolioId").asLong(),
                node.path("baseCurrency").asText(),
                node.path("marketDataAvailable").asBoolean(true),
                items);
    }

    @Override
    public Optional<BigDecimal> priceAlertThreshold(String token) {
        JsonNode node = objectMapper.readTree(get("/api/settings", token));
        return Optional.ofNullable(nullableDecimal(node, "priceAlertThreshold"));
    }

    private String get(String uri, String token) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new TokenExpiredException();
        } catch (HttpClientErrorException.NotFound e) {
            throw new PortfolioServiceUnavailableException("portfolio-service: ресурс не найден", e);
        } catch (RestClientException e) {
            throw new PortfolioServiceUnavailableException("portfolio-service недоступен", e);
        }
    }

    private JsonNode parseArray(String body, String uri) {
        JsonNode array = objectMapper.readTree(body);
        if (!array.isArray()) {
            throw new PortfolioServiceUnavailableException(
                    "portfolio-service вернул некорректный ответ (" + uri + ")", null);
        }
        return array;
    }

    private static BigDecimal nullableDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private static LocalDate localDate(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? LocalDate.parse(value.asText()) : null;
    }
}
