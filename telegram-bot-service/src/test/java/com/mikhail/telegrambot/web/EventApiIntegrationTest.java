package com.mikhail.telegrambot.web;

import com.mikhail.telegrambot.binding.BindingService;
import com.mikhail.telegrambot.bot.RecordingTelegramGateway;
import com.mikhail.telegrambot.portfolio.LinkResult;
import com.mikhail.telegrambot.portfolio.PortfolioClient;
import com.mikhail.telegrambot.support.StubPortfolioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сценарий уведомлений через REST: привязка чата, событие портфеля от
 * portfolio-service и алерт на изменение цены от market-data-service.
 * Telegram не используется — сообщения записывает {@link RecordingTelegramGateway};
 * portfolio-service подменён {@link StubPortfolioClient}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventApiIntegrationTest {

    @TestConfiguration
    static class RecordingGatewayConfiguration {

        @Bean
        @Primary
        RecordingTelegramGateway recordingTelegramGateway() {
            return new RecordingTelegramGateway();
        }

        @Bean
        @Primary
        StubPortfolioClient stubPortfolioClient() {
            return new StubPortfolioClient();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BindingService bindingService;

    @Autowired
    private StubPortfolioClient portfolioClient;

    @Autowired
    private RecordingTelegramGateway gateway;

    @BeforeEach
    void setUp() {
        gateway.clear();
        portfolioClient.portfolios.clear();
        portfolioClient.metrics.clear();
        portfolioClient.calendars.clear();
        portfolioClient.threshold = null;
        portfolioClient.tokenExpired = false;
        portfolioClient.unavailable = false;
        bindingService.unbind(100L);
        bindingService.unbind(200L);
    }

    @Test
    void portfolioEvent_deliversFormattedMessageToBoundChats() throws Exception {
        bindingService.bind(100L, link());
        bindingService.bind(200L, link());

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("POSITION_CREATED", 1)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.delivered").value(2));

        assertThat(gateway.messages()).hasSize(2);
        assertThat(gateway.messages())
                .allSatisfy(message -> assertThat(message.text()).contains("SBER"));
    }

    @Test
    void priceChange_aboveThreshold_deliversAlertToHoldingChat() throws Exception {
        bindingService.bind(100L, link());
        portfolioClient.addPortfolioWithTicker(1L, "SBER");

        mockMvc.perform(post("/api/events/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priceJson("6.2"))
                        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.delivered").value(1));

        assertThat(gateway.messages()).hasSize(1);
        assertThat(gateway.messages().getFirst().text()).contains("SBER").contains("Порог алерта");
    }

    @Test
    void priceChange_belowThreshold_deliversNothing() throws Exception {
        bindingService.bind(100L, link());
        portfolioClient.addPortfolioWithTicker(1L, "SBER");

        mockMvc.perform(post("/api/events/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priceJson("2.0")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.delivered").value(0));

        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void priceChange_withoutTicker_isRejected() throws Exception {
        mockMvc.perform(post("/api/events/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event":"PRICE_CHANGED","newPrice":310.5,"changePercent":6.2}
                                """))
                .andExpect(status().isBadRequest());
    }

    private static LinkResult link() {
        return new LinkResult(7L, "user@example.com", "jwt-token", 2592000,
                new BigDecimal("5"));
    }

    private static String eventJson(String eventType, long portfolioId) {
        return """
                {"eventType":"%s","portfolioId":%d,"ticker":"SBER","quantity":10,
                 "averagePrice":310.5,"currency":"RUB"}
                """.formatted(eventType, portfolioId);
    }

    private static String priceJson(String changePercent) {
        return """
                {"event":"PRICE_CHANGED","ticker":"SBER","previousPrice":295.6,
                 "newPrice":310.5,"changePercent":%s,"currency":"RUB","source":"moex-iss"}
                """.formatted(changePercent);
    }
}
