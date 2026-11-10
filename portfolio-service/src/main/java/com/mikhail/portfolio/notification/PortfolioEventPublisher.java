package com.mikhail.portfolio.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Отправка событий портфеля в telegram-bot-service по REST-вебхуку.
 *
 * <p>Выключен по умолчанию: пока notifications.webhook-url пуст, вызовы
 * publish() — no-op и портфельный API работает без сервиса уведомлений.
 * Сбой вебхука логируется и не прерывает операцию с портфелем.
 */
@Component
public class PortfolioEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PortfolioEventPublisher.class);

    private final String webhookUrl;
    private final RestClient restClient;

    public PortfolioEventPublisher(RestClient.Builder restClientBuilder,
                                   @Value("${notifications.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl == null || webhookUrl.isBlank() ? null : webhookUrl.trim();
        this.restClient = this.webhookUrl == null ? null : restClientBuilder.build();
    }

    /** Отправляет событие; при выключенном вебхуке или сбое сети не бросает исключений. */
    public void publish(NotificationEvent event) {
        if (restClient == null) {
            return;
        }
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Не удалось отправить событие {} для портфеля {}: {}",
                    event.eventType(), event.portfolioId(), e.getMessage());
        }
    }
}
