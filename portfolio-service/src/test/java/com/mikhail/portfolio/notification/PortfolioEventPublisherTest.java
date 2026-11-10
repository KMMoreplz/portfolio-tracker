package com.mikhail.portfolio.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Контракт вебхука событий: при заданном notifications.webhook-url
 * событие уходит POST'ом на /api/events, без URL — ничего не отправляется.
 * Запросы перехватываются MockRestServiceServer, сеть не используется.
 */
class PortfolioEventPublisherTest {

    private static final String WEBHOOK_URL = "http://notifications.local/api/events";

    @Test
    void publish_sendsEventJsonToWebhook() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.eventType").value("POSITION_CREATED"))
                .andExpect(jsonPath("$.portfolioId").value(1))
                .andExpect(jsonPath("$.positionId").value(5))
                .andExpect(jsonPath("$.ticker").value("SBER"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andRespond(withSuccess("{\"delivered\":1}", MediaType.APPLICATION_JSON));

        PortfolioEventPublisher publisher = new PortfolioEventPublisher(builder, WEBHOOK_URL);
        publisher.publish(new NotificationEvent(EventType.POSITION_CREATED, 1L, 5L,
                "SBER", new BigDecimal("10"), new BigDecimal("310.5"), "RUB",
                OffsetDateTime.parse("2026-11-10T18:00:00+03:00")));

        server.verify();
    }

    @Test
    void publish_withoutWebhookUrl_makesNoHttpRequest() {
        RestClient.Builder builder = RestClient.builder();
        // сервер без ожиданий: любой выполненный запрос уронил бы verify()
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        PortfolioEventPublisher publisher = new PortfolioEventPublisher(builder, " ");
        publisher.publish(new NotificationEvent(EventType.PORTFOLIO_DELETED, 7L,
                null, null, null, null, null, OffsetDateTime.now()));

        server.verify();
    }

    @Test
    void publish_whenWebhookUnreachable_doesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        // несуществующий порт: соединение отклоняется, операция не должна падать
        PortfolioEventPublisher publisher = new PortfolioEventPublisher(
                builder, "http://localhost:1/api/events");
        assertThatNoException().isThrownBy(() ->
                publisher.publish(new NotificationEvent(EventType.POSITION_CREATED, 1L, 5L,
                        "SBER", new BigDecimal("10"), new BigDecimal("310.5"), "RUB",
                        OffsetDateTime.now())));
    }
}
