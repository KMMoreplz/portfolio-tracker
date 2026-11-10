package com.mikhail.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Настройки пользователя: порог алертов и одноразовый код привязки Telegram-бота.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SettingsApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // настройки не зависят от market-data — интеграцию выключаем явно
        registry.add("app.market-data.base-url", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void registerUser() throws Exception {
        if (token == null) {
            token = register();
        }
    }

    private String bearer() {
        return "Bearer " + token;
    }

    @Test
    void defaults_areNullUntilSaved() throws Exception {
        mockMvc.perform(get("/api/settings").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAlertThreshold").value(nullValue()))
                .andExpect(jsonPath("$.telegramLinkCode").value(nullValue()))
                .andExpect(jsonPath("$.telegramLinkCodeExpiresAt").value(nullValue()));
    }

    @Test
    void threshold_savedAndCleared() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceAlertThreshold\": 2.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAlertThreshold").value(2.5));

        mockMvc.perform(get("/api/settings").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAlertThreshold").value(2.5));

        // null сбрасывает порог
        mockMvc.perform(put("/api/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceAlertThreshold\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAlertThreshold").value(nullValue()));
    }

    @Test
    void threshold_outOfRange_returns400WithFieldError() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceAlertThreshold\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("priceAlertThreshold"));

        mockMvc.perform(put("/api/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceAlertThreshold\": 101}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void linkCode_generatedWithExpiryAndPersisted() throws Exception {
        MvcResult generated = mockMvc.perform(post("/api/settings/telegram/link-code")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramLinkCode").value(matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$.telegramLinkCodeExpiresAt").isNotEmpty())
                .andReturn();
        String code = objectMapper.readTree(generated.getResponse().getContentAsString())
                .get("telegramLinkCode").asString();

        // код сохранён и читается через GET, пока не истёк
        mockMvc.perform(get("/api/settings").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramLinkCode").value(code));

        // повторный запрос выдаёт новый код (прежний перестаёт действовать)
        mockMvc.perform(post("/api/settings/telegram/link-code")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramLinkCode").value(matchesPattern("\\d{6}")));
    }

    @Test
    void settings_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/settings/telegram/link-code"))
                .andExpect(status().isUnauthorized());
    }

    private String register() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "settings-test-password"}
                                """.formatted("settings-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asString();
    }
}
