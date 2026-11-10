package com.mikhail.portfolio.marketdata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Клиент market-data-service: дивиденды и история котировок по тикеру.
 *
 * Адрес задаётся свойством {@code app.market-data.base-url}; пустой адрес —
 * интеграция не настроена, вызовы бросают {@link MarketDataUnavailableException}.
 * Таймауты короткие: недоступный источник не должен подвешивать ответ API.
 */
@Component
public class MarketDataClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private final RestClient restClient;
    private final boolean configured;

    public MarketDataClient(RestClient.Builder restClientBuilder,
                            @Value("${app.market-data.base-url:}") String baseUrl) {
        this.configured = baseUrl != null && !baseUrl.isBlank();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
                .baseUrl(configured ? baseUrl : "http://localhost")
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Предстоящие и прошедшие выплаты по тикеру.
     * Пустой Optional — тикер неизвестен источнику (404), это не ошибка доступа.
     */
    public Optional<MarketDataDividends> dividends(String ticker) {
        if (!configured) {
            throw new MarketDataUnavailableException("app.market-data.base-url не задан");
        }
        try {
            MarketDataDividends body = restClient.get()
                    .uri("/api/dividends/{ticker}", ticker)
                    .retrieve()
                    .body(MarketDataDividends.class);
            return Optional.ofNullable(body);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new MarketDataUnavailableException("market-data-service недоступен", e);
        }
    }

    /**
     * Дневные свечи по тикеру за период.
     * Пустой Optional — у тикера нет истории (404), это не ошибка доступа.
     */
    public Optional<List<MarketDataCandle>> history(String ticker, LocalDate from, LocalDate to) {
        if (!configured) {
            throw new MarketDataUnavailableException("app.market-data.base-url не задан");
        }
        try {
            MarketDataCandle[] body = restClient.get()
                    .uri("/api/quotes/{ticker}/history?from={from}&to={to}", ticker, from, to)
                    .retrieve()
                    .body(MarketDataCandle[].class);
            return body == null ? Optional.empty() : Optional.of(List.of(body));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new MarketDataUnavailableException("market-data-service недоступен", e);
        }
    }
}
