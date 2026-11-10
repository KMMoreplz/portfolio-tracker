package com.mikhail.telegrambot.bot;

import com.mikhail.telegrambot.config.TelegramProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Проверка шлюза без реального Telegram: без токена ни одного запроса,
 * с токеном — корректный POST к Bot API.
 */
class TelegramApiGatewayTest {

    private static final String API_BASE = "https://api.telegram.org";

    @Test
    void sendMessage_withoutToken_makesNoHttpRequest() {
        var builder = RestClient.builder();
        // сервер без ожиданий: любой выполненный запрос уронил бы verify()
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramGateway gateway = gateway("", builder.build());

        gateway.sendMessage(42L, "hello");

        server.verify();
    }

    @Test
    void sendMessage_withToken_postsChatIdAndText() {
        var builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_BASE + "/botTEST_TOKEN/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value(42))
                .andExpect(jsonPath("$.text").value("hello"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        gateway("TEST_TOKEN", builder.build()).sendMessage(42L, "hello");

        server.verify();
    }

    private static TelegramGateway gateway(String token, RestClient restClient) {
        return new TelegramApiGateway(
                new TelegramProperties(token, API_BASE, 30, 1000),
                new ObjectMapper(),
                restClient);
    }
}
