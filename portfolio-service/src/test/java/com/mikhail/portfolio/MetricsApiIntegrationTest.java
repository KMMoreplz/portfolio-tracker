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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Метрики портфеля: оценка по котировке заглушки, конвертация валют,
 * фолбэк на среднюю цену без котировки, доли позиций и дивиденды.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MetricsApiIntegrationTest {

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
    void singlePosition_valuedByStubQuote() throws Exception {
        long portfolioId = createPortfolio("Одна бумага", "RUB");
        buy(portfolioId, "SBER", "10", "300", null, null);

        // SBER: 10 × 310.50 (заглушка) = 3105.00 против вложенных 3000.00
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("RUB"))
                .andExpect(jsonPath("$.totalValue").value(3105.00))
                .andExpect(jsonPath("$.totalCost").value(3000.00))
                .andExpect(jsonPath("$.profit").value(105.00))
                .andExpect(jsonPath("$.profitPercent").value(3.50))
                .andExpect(jsonPath("$.dividendsReceived").value(0.00))
                .andExpect(jsonPath("$.positions.length()").value(1))
                .andExpect(jsonPath("$.positions[0].ticker").value("SBER"))
                .andExpect(jsonPath("$.positions[0].priceSource").value("in-memory-stub"))
                .andExpect(jsonPath("$.positions[0].priceAsOf").isNotEmpty())
                .andExpect(jsonPath("$.positions[0].currentPrice").value(310.50))
                .andExpect(jsonPath("$.positions[0].weightPercent").value(100.00))
                .andExpect(jsonPath("$.positions[0].valueInBase").value(3105.00))
                .andExpect(jsonPath("$.calculatedAt").isNotEmpty());
    }

    @Test
    void multiCurrencyPosition_convertedToBaseCurrency() throws Exception {
        long portfolioId = createPortfolio("Валютный", "RUB");
        // AAPL торгуется в USD: 2 × 235.60 USD = 471.20 USD × 90.50 = 42643.60 RUB
        buy(portfolioId, "AAPL", "2", "200", null, "USD");

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(42643.60))
                .andExpect(jsonPath("$.totalCost").value(36200.00))
                .andExpect(jsonPath("$.profit").value(6443.60))
                .andExpect(jsonPath("$.profitPercent").value(17.80))
                .andExpect(jsonPath("$.positions[0].currency").value("USD"))
                .andExpect(jsonPath("$.positions[0].value").value(471.20))
                .andExpect(jsonPath("$.positions[0].cost").value(400.00))
                .andExpect(jsonPath("$.positions[0].valueInBase").value(42643.60))
                .andExpect(jsonPath("$.positions[0].weightPercent").value(100.00));
    }

    @Test
    void unknownTicker_valuedAtAveragePriceAndFlagged() throws Exception {
        long portfolioId = createPortfolio("Без котировки", "RUB");
        buy(portfolioId, "ZZZZ", "5", "100", null, null);

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(500.00))
                .andExpect(jsonPath("$.totalCost").value(500.00))
                .andExpect(jsonPath("$.profit").value(0.00))
                .andExpect(jsonPath("$.profitPercent").value(0.00))
                .andExpect(jsonPath("$.positions[0].priceSource").value("average-price"))
                .andExpect(jsonPath("$.positions[0].priceAsOf").doesNotExist())
                .andExpect(jsonPath("$.positions[0].currentPrice").value(100));
    }

    @Test
    void weightsDividendsAndTwoPositions() throws Exception {
        long portfolioId = createPortfolio("Две бумаги", "RUB");
        buy(portfolioId, "SBER", "10", "300", null, null);
        buy(portfolioId, "GAZP", "100", "150", null, null);
        // дивиденд по SBER: 10 бумаг × 15 = 150
        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "DIVIDEND", "ticker": "SBER",
                                 "quantity": "10", "price": "15"}
                                """))
                .andExpect(status().isCreated());

        // GAZP: 100 × 160.25 = 16025.00; SBER: 10 × 310.50 = 3105.00
        // всего 19130.00 против вложенных 18000.00; доли 83.77 и 16.23
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(19130.00))
                .andExpect(jsonPath("$.totalCost").value(18000.00))
                .andExpect(jsonPath("$.profit").value(1130.00))
                .andExpect(jsonPath("$.profitPercent").value(6.28))
                .andExpect(jsonPath("$.dividendsReceived").value(150.00))
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.positions[0].ticker").value("GAZP"))
                .andExpect(jsonPath("$.positions[0].weightPercent").value(83.77))
                .andExpect(jsonPath("$.positions[1].ticker").value("SBER"))
                .andExpect(jsonPath("$.positions[1].weightPercent").value(16.23));
    }

    @Test
    void emptyPortfolio_returnsZeroTotals() throws Exception {
        long portfolioId = createPortfolio("Пустой", "RUB");

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(0.00))
                .andExpect(jsonPath("$.totalCost").value(0.00))
                .andExpect(jsonPath("$.profit").value(0.00))
                .andExpect(jsonPath("$.profitPercent").doesNotExist())
                .andExpect(jsonPath("$.positions.length()").value(0));
    }

    @Test
    void metrics_missingPortfolio_returns404() throws Exception {
        mockMvc.perform(get("/api/portfolios/999999/metrics"))
                .andExpect(status().isNotFound());
    }

    private void buy(long portfolioId, String ticker, String quantity, String price,
                     String commission, String currency) throws Exception {
        String commissionJson = commission == null ? "" : ", \"commission\": \"" + commission + "\"";
        String currencyJson = currency == null ? "" : ", \"currency\": \"" + currency + "\"";
        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "BUY", "ticker": "%s", "quantity": "%s", "price": "%s"%s%s}
                                """.formatted(ticker, quantity, price, commissionJson, currencyJson)))
                .andExpect(status().isCreated());
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
                                {"email": "%s", "password": "metrics-test-password"}
                                """.formatted("metrics-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asString();
    }
}
