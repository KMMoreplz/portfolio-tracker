package com.mikhail.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD операций: покупка и продажа ведут позицию (средневзвешенная с комиссией),
 * дивиденд создаёт событие календаря, изменение и удаление откатывают эффект.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    private MockMvc mockMvc;

    private static String authToken;

    @BeforeEach
    void setUpAuthenticatedMockMvc() throws Exception {
        if (authToken == null) {
            authToken = registerOnce();
        }
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .build();
    }

    @Test
    void buy_updatesPositionAndFillsSecurities() throws Exception {
        long portfolioId = createPortfolio("Покупки", "RUB");

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "BUY", "ticker": "sber", "quantity": "10",
                                 "price": "300", "commission": "50"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.ticker").value("SBER"))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.commission").value(50));

        // средневзвешенная включает комиссию: (10 × 300 + 50) / 10 = 305
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("SBER"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].averagePrice").value(305.0));

        // тикер попал в справочник, имя взято из рыночного справочника
        mockMvc.perform(get("/api/securities").param("query", "sber"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("SBER"))
                .andExpect(jsonPath("$[0].name").value("Сбербанк"));
    }

    @Test
    void sell_reducesPositionWithoutChangingAveragePrice() throws Exception {
        long portfolioId = createPortfolio("Продажи", "RUB");
        buy(portfolioId, "SBER", "10", "300", null);
        buy(portfolioId, "SBER", "10", "320", null);

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SELL", "ticker": "SBER", "quantity": "5", "price": "330"}
                                """))
                .andExpect(status().isCreated());

        // средневзвешенная от продажи не меняется: (10×300 + 10×320) / 20 = 310
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions"))
                .andExpect(jsonPath("$[0].quantity").value(15))
                .andExpect(jsonPath("$[0].averagePrice").value(310.0));
    }

    @Test
    void sell_withoutPosition_returns409() throws Exception {
        long portfolioId = createPortfolio("Продажа без позиции", "RUB");

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SELL", "ticker": "GAZP", "quantity": "1", "price": "160"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("нет позиции")));
    }

    @Test
    void sell_moreThanHeld_returns409() throws Exception {
        long portfolioId = createPortfolio("Перепродажа", "RUB");
        buy(portfolioId, "SBER", "10", "300", null);

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SELL", "ticker": "SBER", "quantity": "11", "price": "300"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("недостаточно бумаг")));

        // позиция не изменилась
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions"))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    void dividend_recordsEventAndKeepsPosition() throws Exception {
        long portfolioId = createPortfolio("Дивиденды", "RUB");
        buy(portfolioId, "SBER", "10", "300", null);

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "DIVIDEND", "ticker": "SBER",
                                 "quantity": "10", "price": "15", "executedAt": "2026-10-15T14:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DIVIDEND"));

        // позиция не изменилась
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions"))
                .andExpect(jsonPath("$[0].quantity").value(10));

        // событие календаря создано: 10 бумаг × 15 = 150
        Integer events = jdbcClient.sql("""
                        SELECT count(*) FROM dividend_events
                        WHERE portfolio_id = :portfolioId AND total_amount = 150
                        """)
                .param("portfolioId", portfolioId)
                .query(Integer.class)
                .single();
        assertThat(events).isEqualTo(1);
    }

    @Test
    void update_replacesEffectOfOperation() throws Exception {
        long portfolioId = createPortfolio("Изменение", "RUB");
        long transactionId = buy(portfolioId, "SBER", "10", "300", null);

        mockMvc.perform(put("/api/portfolios/" + portfolioId + "/transactions/" + transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "BUY", "ticker": "SBER", "quantity": "5", "price": "320"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.price").value(320));

        // старый эффект (10 по 300) откачен, новый (5 по 320) применён
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions"))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[0].averagePrice").value(320.0));
    }

    @Test
    void delete_reversesEffectAndRemovesOperation() throws Exception {
        long portfolioId = createPortfolio("Удаление операции", "RUB");
        long transactionId = buy(portfolioId, "SBER", "10", "300", null);

        mockMvc.perform(delete("/api/portfolios/" + portfolioId + "/transactions/" + transactionId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_filtersByTypeAndMissingReturns404() throws Exception {
        long portfolioId = createPortfolio("Фильтр", "RUB");
        buy(portfolioId, "SBER", "10", "300", null);
        buy(portfolioId, "SBER", "5", "320", null);
        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SELL", "ticker": "SBER", "quantity": "3", "price": "340"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/transactions")
                        .param("type", "SELL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SELL"));

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/transactions"))
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/transactions/999999"))
                .andExpect(status().isNotFound());
    }

    private long buy(long portfolioId, String ticker, String quantity, String price,
                     String commission) throws Exception {
        String commissionJson = commission == null ? "" : ", \"commission\": \"" + commission + "\"";
        MvcResult result = mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "BUY", "ticker": "%s", "quantity": "%s", "price": "%s"%s}
                                """.formatted(ticker, quantity, price, commissionJson)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
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

    private String registerOnce() throws Exception {
        MockMvc rawMockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        MvcResult result = rawMockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "transaction-test-password"}
                                """.formatted("transactions-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asString();
    }
}
