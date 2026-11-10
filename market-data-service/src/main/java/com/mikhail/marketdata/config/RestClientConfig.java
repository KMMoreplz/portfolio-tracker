package com.mikhail.marketdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RestClient'ы внешних источников с таймаутами соединения и чтения.
 * Ответы читаются как String и разбираются вручную (Jackson 3), чтобы не зависеть
 * от настройки конвертеров сообщений.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient tInvestRestClient(TInvestProperties properties) {
        return RestClient.builder()
                .baseUrl(withTrailingSlash(properties.baseUrl()))
                .requestFactory(requestFactory(
                        properties.connectTimeoutMs(),
                        properties.readTimeoutMs()))
                .defaultHeader("Authorization", "Bearer " + properties.token())
                .build();
    }

    @Bean
    public RestClient moexRestClient(MoexProperties properties) {
        return RestClient.builder()
                .baseUrl(withTrailingSlash(properties.baseUrl()))
                .requestFactory(requestFactory(
                        properties.connectTimeoutMs(),
                        properties.readTimeoutMs()))
                .build();
    }

    @Bean
    public RestClient smartLabRestClient(MarketDataProperties properties) {
        MarketDataProperties.SmartLab smartLab = properties.smartLab();
        return RestClient.builder()
                .baseUrl(withTrailingSlash(smartLab.baseUrl()))
                .requestFactory(requestFactory(
                        smartLab.connectTimeoutMs(),
                        smartLab.readTimeoutMs()))
                .build();
    }

    @Bean
    public RestClient newsRestClient(MarketDataProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(
                        properties.smartLab().connectTimeoutMs(),
                        properties.smartLab().readTimeoutMs()))
                .build();
    }

    private JdkClientHttpRequestFactory requestFactory(long connectTimeoutMs, long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    /** Без завершающего слэша относительный uri терял бы сегмент /rest у base url. */
    private String withTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }
}
