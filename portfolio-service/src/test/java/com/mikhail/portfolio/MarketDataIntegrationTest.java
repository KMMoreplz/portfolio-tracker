package com.mikhail.portfolio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Дивидендный календарь и история стоимости: portfolio-service ходит в market-data-service
 * за будущими выплатами и дневными закрытиями. Вместо внешнего сервиса — локальный стаб
 * на JDK HttpServer, которым управляют тесты (штатный режим и режим «источник лёг»).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MarketDataIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    /** Стаб market-data-service: дивиденды и свечи по SBER, для остального — 404. */
    private static final HttpServer STAB;

    /** true — стаб отвечает 500, имитируя упавший market-data-service. */
    private static volatile boolean down;

    static {
        try {
            STAB = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            STAB.createContext("/", MarketDataIntegrationTest::handle);
            STAB.start();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось запустить стаб market-data-service", e);
        }
    }

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.market-data.base-url",
                () -> "http://localhost:" + STAB.getAddress().getPort());
    }

    @AfterAll
    static void stopStab() {
        STAB.stop(0);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpAuthenticatedMockMvc() throws Exception {
        down = false;
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + register()))
                .build();
    }

    @AfterEach
    void restoreStab() {
        down = false;
    }

    // --- дивидендный календарь ---

    @Test
    void emptyPortfolio_calendarIsEmptyAndAvailable() throws Exception {
        long portfolioId = createPortfolio("Пустой календарь");

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/dividend-calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketDataAvailable").value(true))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void position_calendarShowsUpcomingPayoutMultipliedByQuantity() throws Exception {
        long portfolioId = createPortfolio("Сбер");
        buy(portfolioId, "SBER", "10", "300");

        // 10 бумаг × 15.50 = 155.00 RUB к выплате через 10 дней
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/dividend-calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketDataAvailable").value(true))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].ticker").value("SBER"))
                .andExpect(jsonPath("$.items[0].amountPerShare").value(15.50))
                .andExpect(jsonPath("$.items[0].currency").value("RUB"))
                .andExpect(jsonPath("$.items[0].payout").value(155.00))
                .andExpect(jsonPath("$.items[0].payoutInBase").value(155.00))
                .andExpect(jsonPath("$.items[0].paymentDate").value(TODAY.plusDays(10).toString()))
                .andExpect(jsonPath("$.items[0].exDate").value(TODAY.plusDays(3).toString()));
    }

    @Test
    void sourceDown_calendarFlaggedUnavailableInsteadOfSilentEmpty() throws Exception {
        long portfolioId = createPortfolio("Источник лёг");
        buy(portfolioId, "SBER", "10", "300");
        down = true;

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/dividend-calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketDataAvailable").value(false))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void calendar_missingPortfolio_returns404() throws Exception {
        mockMvc.perform(get("/api/portfolios/999999/dividend-calendar"))
                .andExpect(status().isNotFound());
    }

    // --- история стоимости ---

    @Test
    void valueHistory_pricesFromMarketDataHistory() throws Exception {
        long portfolioId = createPortfolio("График");
        buy(portfolioId, "SBER", "10", "300");

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/value-history?days=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceSource").value("market-data"))
                .andExpect(jsonPath("$.points.length()").value(7))
                // за сегодня: 10 × 300.50 (закрытие стаба), вложено 3000.00
                .andExpect(jsonPath("$.points[6].date").value(TODAY.toString()))
                .andExpect(jsonPath("$.points[6].totalValue").value(3005.00))
                .andExpect(jsonPath("$.points[6].invested").value(3000.00))
                // до покупки портфель пуст
                .andExpect(jsonPath("$.points[0].totalValue").value(0.00))
                .andExpect(jsonPath("$.points[0].invested").value(0.00));
    }

    @Test
    void valueHistory_sourceDown_fallsBackToAveragePrice() throws Exception {
        long portfolioId = createPortfolio("График без источника");
        buy(portfolioId, "SBER", "10", "300");
        down = true;

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/value-history?days=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceSource").value("local-fallback"))
                .andExpect(jsonPath("$.points.length()").value(7))
                // котировок нет — цена по средней цене покупки: 10 × 300
                .andExpect(jsonPath("$.points[6].totalValue").value(3000.00))
                .andExpect(jsonPath("$.points[6].invested").value(3000.00));
    }

    @Test
    void valueHistory_withoutOperations_returnsNoData() throws Exception {
        long portfolioId = createPortfolio("Нет операций");

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/value-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceSource").value("no-data"))
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    void valueHistory_daysClampedToSourceLimits() throws Exception {
        long portfolioId = createPortfolio("Длинный период");
        buy(portfolioId, "SBER", "10", "300");

        // days больше максимума не ошибка, а ограничение: 365 точек
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/value-history?days=500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(365));
    }

    // --- стаб market-data-service ---

    private static void handle(HttpExchange exchange) throws IOException {
        if (down) {
            respond(exchange, 500, "{\"error\": \"down\"}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/dividends/SBER")) {
            respond(exchange, 200, """
                    {"ticker": "SBER", "source": "stab",
                     "upcoming": [{"ticker": "SBER", "exDate": "%s", "paymentDate": "%s",
                                   "amountPerShare": 15.50, "currency": "RUB",
                                   "status": "UPCOMING", "source": "stab"}],
                     "past": []}
                    """.formatted(TODAY.plusDays(3), TODAY.plusDays(10)));
            return;
        }
        if (path.startsWith("/api/quotes/SBER/history")) {
            StringBuilder candles = new StringBuilder("[");
            for (int i = 6; i >= 0; i--) {
                if (i < 6) {
                    candles.append(',');
                }
                candles.append("""
                        {"date": "%s", "open": 300.00, "high": 301.00,
                         "low": 299.00, "close": 300.50, "volume": 1000}
                        """.formatted(TODAY.minusDays(i)));
            }
            candles.append(']');
            respond(exchange, 200, candles.toString());
            return;
        }
        respond(exchange, 404, "{\"error\": \"not found\"}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // --- помощники ---

    private long createPortfolio(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "baseCurrency": "RUB"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private void buy(long portfolioId, String ticker, String quantity, String price) throws Exception {
        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "BUY", "ticker": "%s", "quantity": "%s", "price": "%s"}
                                """.formatted(ticker, quantity, price)))
                .andExpect(status().isCreated());
    }

    private String register() throws Exception {
        MockMvc rawMockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        MvcResult result = rawMockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "market-data-test-password"}
                                """.formatted("marketdata-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asString();
    }
}
