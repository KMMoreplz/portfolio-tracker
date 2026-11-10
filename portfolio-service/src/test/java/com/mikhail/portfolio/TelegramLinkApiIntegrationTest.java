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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Привязка Telegram-бота по одноразовому коду: обмен кода на долгоживущий
 * JWT. Маршрут открыт без токена, код одноразовый и действует 15 минут.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TelegramLinkApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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

    @Test
    void link_withValidCode_returnsWorkingToken() throws Exception {
        String code = generateCode();

        MvcResult linked = mockMvc.perform(post("/api/telegram/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(2592000))
                .andReturn();
        JsonNode body = objectMapper.readTree(linked.getResponse().getContentAsString());
        String botToken = body.get("token").asString();

        // выданный токен работает как обычный JWT пользователя
        mockMvc.perform(get("/api/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken))
                .andExpect(status().isOk());
    }

    @Test
    void link_returnsUserThreshold() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceAlertThreshold\": 7.5}"))
                .andExpect(status().isOk());

        String code = generateCode();
        mockMvc.perform(post("/api/telegram/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAlertThreshold").value(7.5));
    }

    @Test
    void link_codeIsOneTime() throws Exception {
        String code = generateCode();

        mockMvc.perform(post("/api/telegram/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(code)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/telegram/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(code)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void link_unknownCode_returns401() throws Exception {
        mockMvc.perform(post("/api/telegram/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void link_badFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/telegram/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    private String generateCode() throws Exception {
        MvcResult generated = mockMvc.perform(post("/api/settings/telegram/link-code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(generated.getResponse().getContentAsString())
                .get("telegramLinkCode").asString();
    }

    private String register() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "link-test-password"}
                                """.formatted("link-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }
}
