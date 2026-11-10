package com.mikhail.telegrambot.bot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Тестовый шлюз: записывает отправленные сообщения вместо обращения
 * к Telegram. Используется в тестах как @Primary-бина.
 */
public class RecordingTelegramGateway implements TelegramGateway {

    public record SentMessage(long chatId, String text) {
    }

    private final List<SentMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void sendMessage(long chatId, String text) {
        messages.add(new SentMessage(chatId, text));
    }

    public List<SentMessage> messages() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }
}
