package com.mikhail.telegrambot.bot;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Разбор ответа Telegram getUpdates в {@link BotUpdate}. Используется
 * {@link JsonNode} вместо привязки к классам: формат ответа Telegram
 * содержит много служебных полей, нужны только chat.id, text и update_id.
 */
@Component
public class UpdateParser {

    private final ObjectMapper objectMapper;

    public UpdateParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @throws JacksonException при некорректном JSON — вызывающая сторона
     *                          (poller) логирует и продолжает цикл
     */
    public List<BotUpdate> parse(String json) {
        JsonNode root = objectMapper.readTree(json);
        JsonNode result = root.path("result");
        if (!result.isArray()) {
            return List.of();
        }
        List<BotUpdate> updates = new ArrayList<>();
        for (JsonNode node : result) {
            JsonNode message = node.path("message");
            if (!message.isObject()) {
                continue;
            }
            JsonNode chatId = message.path("chat").path("id");
            JsonNode text = message.path("text");
            if (!chatId.isNumber() || !text.isTextual()) {
                continue;
            }
            updates.add(new BotUpdate(node.path("update_id").asLong(), chatId.asLong(), text.asText()));
        }
        return updates;
    }
}
