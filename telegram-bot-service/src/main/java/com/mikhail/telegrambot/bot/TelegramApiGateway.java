package com.mikhail.telegrambot.bot;

import com.mikhail.telegrambot.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Map;

/**
 * Отправка сообщений через Telegram Bot API (RestClient, без сторонних
 * bot-библиотек). Без сконфигурированного токена сообщения не отправляются —
 * только пишутся в лог, чтобы локальный запуск не ходил в сеть.
 */
public class TelegramApiGateway implements TelegramGateway {

    private static final Logger log = LoggerFactory.getLogger(TelegramApiGateway.class);

    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TelegramApiGateway(TelegramProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public void sendMessage(long chatId, String text) {
        if (!properties.tokenConfigured()) {
            log.info("TELEGRAM_BOT_TOKEN не задан — сообщение для чата {} не отправлено: {}", chatId, text);
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of("chat_id", chatId, "text", text));
            restClient.post()
                    .uri(sendMessageUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // в лог не попадают ни текст сообщения, ни URL (в URL — токен)
            log.warn("Telegram не принял сообщение для чата {} ({})", chatId, e.getClass().getSimpleName());
        } catch (JacksonException e) {
            log.warn("Не удалось сериализовать сообщение для чата {}", chatId);
        }
    }

    private URI sendMessageUri() {
        return URI.create(properties.apiBaseUrl() + "/bot" + properties.botToken() + "/sendMessage");
    }
}
