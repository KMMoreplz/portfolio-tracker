package com.mikhail.marketdata.event;

import com.mikhail.marketdata.config.MarketDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST-вебхук событий об изменении цены: асинхронный POST на telegram-bot-service
 * ({@code market-data.events.webhook-url}). Если адрес не задан — публикация
 * отключена. Ошибки доставки только логируются: падение получателя не влияет
 * на обновление котировок.
 */
@Component
public class WebhookPriceEventPublisher implements PriceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebhookPriceEventPublisher.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "price-event-publisher");
        thread.setDaemon(true);
        return thread;
    });

    public WebhookPriceEventPublisher(
            RestClient newsRestClient,
            ObjectMapper objectMapper,
            MarketDataProperties properties) {
        this.restClient = newsRestClient;
        this.objectMapper = objectMapper;
        this.webhookUrl = properties.events().webhookUrl();
    }

    @Override
    public void publish(PriceChangedEvent event) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        executor.submit(() -> send(event));
    }

    private void send(PriceChangedEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", PriceChangedEvent.EVENT_TYPE);
            payload.put("ticker", event.ticker());
            payload.put("previousPrice", event.previousPrice());
            payload.put("newPrice", event.newPrice());
            payload.put("changePercent", event.changePercent());
            payload.put("currency", event.currency());
            payload.put("quotedAt", event.quotedAt().toString());
            payload.put("source", event.source());
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Событие об изменении цены {} опубликовано на {}", event.ticker(), webhookUrl);
        } catch (Exception exception) {
            log.warn("Не удалось опубликовать событие об изменении цены {}: {}",
                    event.ticker(), exception.getMessage());
        }
    }
}
