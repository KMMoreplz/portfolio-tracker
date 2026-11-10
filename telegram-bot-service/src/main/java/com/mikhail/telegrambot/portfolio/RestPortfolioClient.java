package com.mikhail.telegrambot.portfolio;

import com.mikhail.telegrambot.error.PortfolioNotFoundException;
import com.mikhail.telegrambot.error.PortfolioServiceUnavailableException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Клиент portfolio-service: REST-чтение портфеля и позиций для команд бота.
 * Тело читается как строка и разбирается вручную через {@link JsonNode} —
 * так формат ответа ядра не связывает бота с его DTO.
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
    public Optional<PortfolioSnapshot> getPortfolio(long portfolioId) {
        try {
            String body = restClient.get()
                    .uri("/api/portfolios/{id}", portfolioId)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body);
            return Optional.of(new PortfolioSnapshot(
                    node.path("id").asLong(),
                    node.path("name").asText(),
                    node.path("baseCurrency").asText()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new PortfolioServiceUnavailableException(
                    "portfolio-service недоступен", e);
        } catch (RuntimeException e) {
            // ошибка разбора тела — трактуем как недоступность, формат ответа сломан
            throw new PortfolioServiceUnavailableException(
                    "portfolio-service вернул некорректный ответ", e);
        }
    }

    @Override
    public List<PositionSnapshot> getPositions(long portfolioId) {
        try {
            String body = restClient.get()
                    .uri("/api/portfolios/{id}/positions", portfolioId)
                    .retrieve()
                    .body(String.class);
            JsonNode array = objectMapper.readTree(body);
            if (!array.isArray()) {
                throw new PortfolioServiceUnavailableException(
                        "portfolio-service вернул некорректный ответ", null);
            }
            List<PositionSnapshot> positions = new ArrayList<>();
            for (JsonNode node : array) {
                positions.add(new PositionSnapshot(
                        node.path("ticker").asText(),
                        node.path("instrumentName").asText(null),
                        decimal(node, "quantity"),
                        decimal(node, "averagePrice"),
                        node.path("currency").asText(null)));
            }
            return positions;
        } catch (HttpClientErrorException.NotFound e) {
            throw new PortfolioNotFoundException(portfolioId);
        } catch (RestClientException e) {
            throw new PortfolioServiceUnavailableException(
                    "portfolio-service недоступен", e);
        } catch (PortfolioNotFoundException | PortfolioServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PortfolioServiceUnavailableException(
                    "portfolio-service вернул некорректный ответ", e);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }
}
