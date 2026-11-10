package com.mikhail.telegrambot.bot;

/**
 * Отправка сообщений в Telegram. Единственная точка выхода наружу:
 * вся бизнес-логика уведомлений работает с этим интерфейсом, поэтому
 * тестируется без обращения к Telegram.
 */
public interface TelegramGateway {

    /**
     * Отправляет текст в чат. Реализация без токена ничего не отправляет.
     */
    void sendMessage(long chatId, String text);
}
