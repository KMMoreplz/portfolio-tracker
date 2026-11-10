package com.mikhail.telegrambot.bot;

/**
 * Обновление Telegram, полученное long polling'ом: идентификатор обновления,
 * идентификатор чата и текст сообщения.
 */
public record BotUpdate(long updateId, long chatId, String text) {
}
