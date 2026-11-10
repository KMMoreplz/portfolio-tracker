package com.mikhail.telegrambot.db;

/**
 * Журнал отправленных уведомлений: чтобы напоминание о дивиденде не
 * отправлялось повторно при каждой проверке расписания.
 */
public interface SentNotificationStore {

    /**
     * Помечает уведомление отправленным.
     *
     * @return true, если это первая отправка; false, если ключ уже встречался
     */
    boolean markSent(long chatId, String dedupeKey);
}
