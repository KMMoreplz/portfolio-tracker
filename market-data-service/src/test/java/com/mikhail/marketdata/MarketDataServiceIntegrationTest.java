package com.mikhail.marketdata;

import com.mikhail.common.marketdata.Quote;
import com.mikhail.marketdata.repository.PriceQuoteRepository;
import com.mikhail.marketdata.resilience.ResilienceExecutor;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты market-data-service: настоящая PostgreSQL в Testcontainers
 * и WireMock вместо внешних API (T-Invest, MOEX ISS, smart-lab, RSS, вебхук).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MarketDataServiceIntegrationTest {

    private static final String FIND_INSTRUMENT_PATH =
            "/tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument";
    private static final String GET_LAST_PRICES_PATH =
            "/tinkoff.public.invest.api.contract.v1.MarketDataService/GetLastPrices";
    private static final String GET_CANDLES_PATH =
            "/tinkoff.public.invest.api.contract.v1.MarketDataService/GetCandles";
    private static final String GET_DIVIDENDS_PATH =
            "/tinkoff.public.invest.api.contract.v1.InstrumentsService/GetDividends";

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String baseUrl = "http://localhost:" + wireMock.port() + "/";
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("t-invest.base-url", () -> baseUrl);
        registry.add("t-invest.token", () -> "test-token");
        registry.add("moex.base-url", () -> baseUrl);
        registry.add("market-data.smart-lab.base-url", () -> baseUrl);
        registry.add("market-data.news.feeds", () -> baseUrl + "rss.xml");
        registry.add("market-data.news.cache-ttl-seconds", () -> "0");
        registry.add("market-data.events.webhook-url", () -> baseUrl + "webhook");
        registry.add("market-data.tickers", () -> "SBER");
        registry.add("market-data.trading-hours.enforced", () -> "false");
        registry.add("market-data.resilience.retry.max-attempts", () -> "1");
        registry.add("market-data.resilience.retry.wait-duration-ms", () -> "10");
        registry.add("market-data.resilience.circuit-breaker.minimum-number-of-calls", () -> "2");
        registry.add("market-data.resilience.circuit-breaker.failure-rate-threshold-percent", () -> "50");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PriceQuoteRepository priceQuoteRepository;

    @Autowired
    private ResilienceExecutor resilienceExecutor;

    @BeforeEach
    void resetState() {
        wireMock.resetAll();
        resilienceExecutor.reset("t-invest");
        resilienceExecutor.reset("moex-iss");
        resilienceExecutor.reset("smart-lab");
        resilienceExecutor.reset("news-rss");
    }

    @Test
    void refresh_savesQuotesFromTInvest_andApiReturnsThem() throws Exception {
        stubInstrument("SBER");
        stubLastPrice("SBER", "300", 500_000_000L, Instant.now());

        mockMvc.perform(post("/api/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.refreshed").isArray());

        mockMvc.perform(get("/api/quotes").param("tickers", "SBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("SBER"))
                .andExpect(jsonPath("$[0].price").value(300.50))
                .andExpect(jsonPath("$[0].currency").value("RUB"))
                .andExpect(jsonPath("$[0].source").value("t-invest"))
                .andExpect(jsonPath("$[0].cached").value(false));

        Integer saved = jdbcClient.sql("""
                SELECT count(*) FROM market_data.price_quotes
                WHERE ticker = 'SBER' AND source = 't-invest'
                """).query(Integer.class).single();
        assertThat(saved).isPositive();
    }

    @Test
    void history_returnsDailyCandlesFromTInvest() throws Exception {
        stubInstrument("SBER");
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo(GET_CANDLES_PATH)).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"candles":[{"open":{"units":"296","nano":300000000},
                        "high":{"units":"297","nano":0},
                        "low":{"units":"295","nano":700000000},
                        "close":{"units":"296","nano":700000000},
                        "volume":1001739,
                        "time":"2026-11-01T00:00:00.000Z"}]}
                        """)));

        mockMvc.perform(get("/api/quotes/SBER/history")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$[0].open").value(296.30))
                .andExpect(jsonPath("$[0].volume").value(1001739));
    }

    @Test
    void externalApiDown_fallsBackToSavedQuotes_andCircuitBreakerOpens() throws Exception {
        priceQuoteRepository.insert(new Quote(
                "SBER", new BigDecimal("250.10"), "RUB", Instant.now(), "t-invest"));
        stubAllSourcesWithServerError();

        // первый и второй запрос: ретраи исчерпаны, circuit breaker t-invest открывается
        mockMvc.perform(get("/api/quotes").param("tickers", "SBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(250.10))
                .andExpect(jsonPath("$[0].cached").value(true));
        mockMvc.perform(get("/api/quotes").param("tickers", "SBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cached").value(true));

        assertThat(resilienceExecutor.breakerState("t-invest")).contains(CircuitBreaker.State.OPEN);

        // третий запрос идёт мимо открытого breaker'а — ответ по-прежнему 200 с fallback
        mockMvc.perform(get("/api/quotes").param("tickers", "SBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(250.10))
                .andExpect(jsonPath("$[0].cached").value(true));
    }

    @Test
    void unknownTicker_returns404() throws Exception {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo(FIND_INSTRUMENT_PATH)).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"instruments\":[]}")));
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/engines/.*")).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"marketdata\":{\"columns\":[],\"data\":[]}}")));
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/securities/.*")).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"description\":{\"columns\":[\"name\",\"value\"],\"data\":[]}}")));

        mockMvc.perform(get("/api/quotes/ZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void dividends_servedFromTInvest() throws Exception {
        stubInstrument("SBER");
        String paymentDate = LocalDate.now(ZoneOffset.UTC).plusDays(25).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        String lastBuyDate = LocalDate.now(ZoneOffset.UTC).plusDays(20).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo(GET_DIVIDENDS_PATH)).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"dividends":[{"declaredDate":"2026-11-01T00:00:00.000Z",
                        "lastBuyDate":"%s","recordDate":"%s","paymentDate":"%s",
                        "dividendNet":{"units":"33","nano":0,"currency":"RUB"},
                        "regularity":"Annual","dividendType":"Regular Cash"}]}
                        """.formatted(lastBuyDate, lastBuyDate, paymentDate))));

        mockMvc.perform(get("/api/dividends/SBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("SBER"))
                .andExpect(jsonPath("$.source").value("t-invest"))
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].amountPerShare").value(33))
                .andExpect(jsonPath("$.upcoming[0].status").value("UPCOMING"));
    }

    @Test
    void dividends_fallBackToSmartLab_whenTInvestFails() throws Exception {
        stubInstrument("SBER");
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo(GET_DIVIDENDS_PATH)).willReturn(WireMock.aResponse().withStatus(500)));
        stubSmartLabPages();

        mockMvc.perform(get("/api/dividends/SBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("smart-lab"))
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].amountPerShare").value(33))
                .andExpect(jsonPath("$.upcoming[0].paymentDate").value(
                        LocalDate.now().plusDays(40).toString()))
                .andExpect(jsonPath("$.past.length()").value(1))
                .andExpect(jsonPath("$.past[0].amountPerShare").value(37.64))
                .andExpect(jsonPath("$.past[0].status").value("PAST"));
    }

    @Test
    void news_aggregatedFromRss_filteredByTicker() throws Exception {
        stubInstrument("SBER");
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/rss.xml")).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/rss+xml")
                .withBody("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <rss version="2.0"><channel><title>Тестовая лента</title>
                        <item>
                          <title>SBER: Сбербанк поднял дивидендную доходность</title>
                          <link>https://example.com/news/1</link>
                          <pubDate>Mon, 10 Nov 2026 12:00:00 +0300</pubDate>
                          <description>Акции Сбербанка выросли на 2%</description>
                        </item>
                        <item>
                          <title>Нефтяные фьючерсы подешевели</title>
                          <link>https://example.com/news/2</link>
                          <pubDate>Tue, 11 Nov 2026 09:00:00 +0300</pubDate>
                          <description>Brent опустился ниже $90</description>
                        </item>
                        </channel></rss>
                        """)));

        mockMvc.perform(get("/api/news").param("tickers", "SBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tickers[0]").value("SBER"))
                .andExpect(jsonPath("$[0].title").value(
                        "SBER: Сбербанк поднял дивидендную доходность"))
                .andExpect(jsonPath("$[0].feed").value(wireMock.baseUrl() + "/rss.xml"));
    }

    @Test
    void priceChange_publishedToWebhook() throws Exception {
        priceQuoteRepository.insert(new Quote(
                "SBER", new BigDecimal("100.00"), "RUB", Instant.now().minusSeconds(60), "t-invest"));
        stubInstrument("SBER");
        stubLastPrice("SBER", "200", 0L, Instant.now());

        mockMvc.perform(post("/api/refresh"))
                .andExpect(status().isAccepted());

        await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                wireMock.findAll(postRequestedFor(WireMock.urlEqualTo("/webhook"))).size() >= 1);

        String body = wireMock.findAll(postRequestedFor(WireMock.urlEqualTo("/webhook"))).get(0)
                .getBodyAsString();
        assertThat(body).contains("PRICE_CHANGED").contains("\"ticker\":\"SBER\"");
    }

    @Test
    void trackedTickers_canBeAddedAndRemoved() throws Exception {
        mockMvc.perform(post("/api/tickers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tickers\":[\"LKOH\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasItem("LKOH")));

        mockMvc.perform(delete("/api/tickers/LKOH"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tickers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("LKOH"))));
    }

    @Test
    void news_matchedByInstrumentNameWords_inGenitiveCase() throws Exception {
        // краткое имя MOEX — «Магнит ао», в новости встречаются только «Магнита»
        stubInstrument("MGNT", "Магнит ао");
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/rss.xml")).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/rss+xml")
                .withBody("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <rss version="2.0"><channel><title>Тестовая лента</title>
                        <item>
                          <title>Акции Магнита подорожали после отчёта</title>
                          <link>https://example.com/news/10</link>
                          <pubDate>Mon, 10 Nov 2026 10:00:00 +0300</pubDate>
                        </item>
                        <item>
                          <title>Нефть подешевела на бирже</title>
                          <link>https://example.com/news/11</link>
                          <pubDate>Mon, 10 Nov 2026 11:00:00 +0300</pubDate>
                        </item>
                        </channel></rss>
                        """)));

        mockMvc.perform(get("/api/news").param("tickers", "MGNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tickers[0]").value("MGNT"))
                .andExpect(jsonPath("$[0].title").value("Акции Магнита подорожали после отчёта"));
    }

    private void stubInstrument(String ticker) {
        stubInstrument(ticker, "Сбербанк");
    }

    private void stubInstrument(String ticker, String name) {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo(FIND_INSTRUMENT_PATH)).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"instruments":[{"uid":"uid-%s","ticker":"%s","name":"%s",
                        "isin":"RU0009029540","lot":10,"classCode":"TQBR",
                        "instrumentType":"share","apiTradeAvailableFlag":true}]}
                        """.formatted(ticker, ticker, name))));
    }

    private void stubLastPrice(String ticker, String units, long nano, Instant time) {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo(GET_LAST_PRICES_PATH)).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"lastPrices":[{"ticker":"%s","instrumentUid":"uid-%s",
                        "price":{"units":"%s","nano":%d},"time":"%s"}]}
                        """.formatted(ticker, ticker, units, nano, time))));
    }

    private void stubAllSourcesWithServerError() {
        wireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/tinkoff\\.public.*")).willReturn(WireMock.aResponse().withStatus(500)));
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/engines/.*")).willReturn(WireMock.aResponse().withStatus(500)));
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/securities/.*")).willReturn(WireMock.aResponse().withStatus(500)));
    }

    private void stubSmartLabPages() {
        // smart-lab отдаёт даты в формате dd.MM.yyyy
        DateTimeFormatter smartLabDate = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String upcomingPayment = LocalDate.now().plusDays(40).format(smartLabDate);
        String upcomingEx = LocalDate.now().plusDays(35).format(smartLabDate);
        String upcomingRecord = LocalDate.now().plusDays(37).format(smartLabDate);
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/dividends/")).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("""
                        <html><body>
                        <table class="simple-little-table financials dividends sort-table">
                        <tr><th>Название</th><th>Тикер</th><th>Период</th><th>Дивиденд</th>
                        <th>Доходность</th><th>СД</th><th>Купить До</th>
                        <th>Реестр</th><th>Выплата</th><th>Цена</th></tr>
                        <tr class="dividend_approved">
                        <td><a href="/q/SBER/dividend/">Сбербанк</a></td>
                        <td>SBER</td><td>2026 год</td><td><strong>33</strong></td>
                        <td><strong>10%%</strong></td><td></td>
                        <td>%s</td><td>%s</td><td>%s</td><td>300,5</td>
                        </tr>
                        </table>
                        </body></html>
                        """.formatted(upcomingEx, upcomingRecord, upcomingPayment))));
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/q/SBER/dividend/")).willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("""
                        <html><body>
                        <table class="simple-little-table financials dividends sort-table">
                        <tr><th>Тикер</th><th>дата T-1</th><th>дата отсечки</th>
                        <th>Период</th><th>дивиденд</th><th>Цена акции</th></tr>
                        <tr><td>SBER</td><td>17.07.2026</td><td>20.07.2026</td>
                        <td>2025 год</td><td><strong>37,64</strong>&#8381;</td><td>277,43</td></tr>
                        <tr><td>SBERP</td><td>17.07.2026</td><td>20.07.2026</td>
                        <td>2025 год</td><td><strong>37,64</strong>&#8381;</td><td>279,81</td></tr>
                        </table>
                        </body></html>
                        """)));
    }
}
