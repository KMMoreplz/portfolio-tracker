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

    /** Попытки отправки одного сообщения и пауза между ними (с ростом номера попытки). */
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final long SEND_RETRY_BACKOFF_MS = 500;

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
        // ретраи на отправку: временный сбой Telegram не должен терять уведомление
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            if (trySend(chatId, text)) {
                return;
            }
            if (attempt < MAX_SEND_ATTEMPTS) {
                try {
                    Thread.sleep(SEND_RETRY_BACKOFF_MS * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.warn("Telegram не принял сообщение для чата {} после {} попыток", chatId, MAX_SEND_ATTEMPTS);
    }

    private boolean trySend(long chatId, String text) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("chat_id", chatId, "text", text));
            restClient.post()
                    .uri(sendMessageUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Сообщение доставлено в чат {}", chatId);
            return true;
        } catch (RestClientException e) {
            // в лог не попадают ни текст сообщения, ни URL (в URL — токен)
            log.warn("Попытка отправки в чат {} не удалась ({})", chatId, e.getClass().getSimpleName());
            return false;
        } catch (JacksonException e) {
            log.warn("Не удалось сериализовать сообщение для чата {}", chatId);
            return true; // повтор не поможет
        }
    }

    private URI sendMessageUri() {
        return URI.create(properties.apiBaseUrl() + "/bot" + properties.botToken() + "/sendMessage");
    }
}
