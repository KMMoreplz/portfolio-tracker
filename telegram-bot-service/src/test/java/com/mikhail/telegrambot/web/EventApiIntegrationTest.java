package com.mikhail.telegrambot.web;

import com.mikhail.telegrambot.bot.RecordingTelegramGateway;
import com.mikhail.telegrambot.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сценарий уведомления через REST: подписка чата на портфель, событие
 * от portfolio-service, доставка сообщения. Telegram не используется —
 * записывается {@link RecordingTelegramGateway}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventApiIntegrationTest {

    @TestConfiguration
    static class RecordingGatewayConfiguration {

        @Bean
        @Primary
        RecordingTelegramGateway recordingTelegramGateway() {
            return new RecordingTelegramGateway();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RecordingTelegramGateway gateway;

    @BeforeEach
    void clearGateway() {
        gateway.clear();
    }

    @Test
    void subscribeThenEvent_deliversFormattedMessageToChat() throws Exception {
        notificationService.subscribe(100L, 1L);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("POSITION_CREATED", 1)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.delivered").value(1));

        assertThat(gateway.messages()).hasSize(1);
        assertThat(gateway.messages().get(0).chatId()).isEqualTo(100L);
        assertThat(gateway.messages().get(0).text())
                .isEqualTo("Портфель #1: добавлена позиция SBER: 10 шт. по 310.5 RUB");
    }

    @Test
    void eventWithoutSubscribers_reportsZeroDelivered() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("POSITION_UPDATED", 42)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.delivered").value(0));

        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void eventAfterUnsubscribe_isNotDelivered() throws Exception {
        notificationService.subscribe(100L, 1L);
        notificationService.unsubscribe(100L, 1L);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("POSITION_DELETED", 1)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.delivered").value(0));

        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void event_missingEventType_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portfolioId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("eventType"));
    }

    @Test
    void event_missingPortfolioId_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"POSITION_CREATED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("portfolioId"));
    }

    @Test
    void event_unknownEventType_returns400() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"NEWS\",\"portfolioId\":1}"))
                .andExpect(status().isBadRequest());
    }

    private static String eventJson(String eventType, long portfolioId) {
        return """
                {"eventType":"%s","portfolioId":%d,"positionId":5,"ticker":"SBER",
                 "quantity":10,"averagePrice":310.5,"currency":"RUB",
                 "occurredAt":"2026-11-10T16:00:00+03:00"}
                """.formatted(eventType, portfolioId);
    }
}
