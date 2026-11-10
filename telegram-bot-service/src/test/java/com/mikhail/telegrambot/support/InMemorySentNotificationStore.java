package com.mikhail.telegrambot.support;

import com.mikhail.telegrambot.db.SentNotificationStore;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Тестовый журнал отправленных уведомлений в памяти. */
public class InMemorySentNotificationStore implements SentNotificationStore {

    private final Set<String> keys = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markSent(long chatId, String dedupeKey) {
        return keys.add(chatId + "|" + dedupeKey);
    }
}
