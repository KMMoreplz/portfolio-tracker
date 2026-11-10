package com.mikhail.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PortfolioApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void createPortfolio_persistsAndReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Основной", "baseCurrency": "RUB"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/api/portfolios/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Основной"))
                .andExpect(jsonPath("$.baseCurrency").value("RUB"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createPortfolio_withoutCurrency_defaultsToRub() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Без валюты"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseCurrency").value("RUB"));
    }

    @Test
    void createPortfolio_blankName_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "baseCurrency": "RUB"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void createPortfolio_invalidCurrency_returns400() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Основной", "baseCurrency": "rub"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("baseCurrency"));
    }

    @Test
    void getPortfolio_missing_returns404() throws Exception {
        mockMvc.perform(get("/api/portfolios/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void portfolioLifecycle_updateAndDelete() throws Exception {
        long portfolioId = createPortfolio("Временный", "RUB");

        mockMvc.perform(put("/api/portfolios/" + portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Переименованный"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Переименованный"))
                .andExpect(jsonPath("$.baseCurrency").value("RUB"));

        mockMvc.perform(delete("/api/portfolios/" + portfolioId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/portfolios/" + portfolioId))
                .andExpect(status().isNotFound());
    }

    @Test
    void addPosition_and_readComposition() throws Exception {
        long portfolioId = createPortfolio("Позиции", "RUB");

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "SBER", "instrumentName": "Сбербанк",
                                 "quantity": "10", "averagePrice": "250.50", "currency": "RUB"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("SBER"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.averagePrice").value(250.50));

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value("SBER"))
                .andExpect(jsonPath("$[0].portfolioId").value(portfolioId));
    }

    @Test
    void addPosition_duplicateTicker_returns409() throws Exception {
        long portfolioId = createPortfolio("Дубликат", "RUB");
        String body = """
                {"ticker": "GAZP", "quantity": "1", "averagePrice": "100"}
                """;

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("GAZP")));
    }

    @Test
    void addPosition_zeroQuantity_returns400() throws Exception {
        long portfolioId = createPortfolio("Валидация", "RUB");

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "VTBR", "quantity": "0", "averagePrice": "1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
    }

    @Test
    void addPosition_invalidTicker_returns400() throws Exception {
        long portfolioId = createPortfolio("Тикер", "RUB");

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker": "sber!", "quantity": "1", "averagePrice": "1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("ticker"));
    }

    @Test
    void updatePosition_changesQuantityAndPrice_keepsUnspecifiedFields() throws Exception {
        long portfolioId = createPortfolio("Изменение", "USD");
        long positionId = addPosition(portfolioId, """
                {"ticker": "AAPL", "instrumentName": "Apple", "quantity": "5", "averagePrice": "100", "currency": "USD"}
                """);

        mockMvc.perform(put("/api/portfolios/" + portfolioId + "/positions/" + positionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": "7", "averagePrice": "110.25"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.averagePrice").value(110.25))
                .andExpect(jsonPath("$.instrumentName").value("Apple"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void deletePosition_removesItFromComposition() throws Exception {
        long portfolioId = createPortfolio("Удаление", "RUB");
        long positionId = addPosition(portfolioId, """
                {"ticker": "LKOH", "quantity": "2", "averagePrice": "7000"}
                """);

        mockMvc.perform(delete("/api/portfolios/" + portfolioId + "/positions/" + positionId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions/" + positionId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePortfolio_cascadesPositionDeletion() throws Exception {
        long portfolioId = createPortfolio("Каскад", "RUB");
        addPosition(portfolioId, """
                {"ticker": "MOEX", "quantity": "3", "averagePrice": "200"}
                """);

        mockMvc.perform(delete("/api/portfolios/" + portfolioId))
                .andExpect(status().isNoContent());

        assertThat(countPositions(portfolioId)).isZero();
    }

    private int countPositions(long portfolioId) {
        return jdbcClient.sql("SELECT count(*) FROM position WHERE portfolio_id = :portfolioId")
                .param("portfolioId", portfolioId)
                .query(Integer.class)
                .single();
    }

    private long createPortfolio(String name, String baseCurrency) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "baseCurrency": "%s"}
                                """.formatted(name, baseCurrency)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private long addPosition(long portfolioId, String jsonBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/portfolios/" + portfolioId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
