package com.mikhail.telegrambot.config;

import com.mikhail.telegrambot.bot.TelegramApiGateway;
import com.mikhail.telegrambot.bot.TelegramGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * HTTP-клиент Telegram Bot API и шлюз отправки сообщений. Один клиент
 * на оба сценария (getUpdates и sendMessage): long polling держит
 * соединение до poll-timeout-seconds, поэтому read-timeout с запасом.
 *
 * <p>Spring Boot 4.1 не даёт бин RestClient.Builder со spring-boot-starter-web,
 * поэтому клиент собирается здесь вручную.
 */
@Configuration
public class TelegramClientConfig {

    @Bean
    public RestClient telegramRestClient(TelegramProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        // getUpdates — long polling: чтение должно переживать poll-timeout-seconds
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.pollTimeoutSeconds() + 10));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public TelegramGateway telegramGateway(TelegramProperties properties,
                                           ObjectMapper objectMapper,
                                           RestClient telegramRestClient) {
        return new TelegramApiGateway(properties, objectMapper, telegramRestClient);
    }
}
