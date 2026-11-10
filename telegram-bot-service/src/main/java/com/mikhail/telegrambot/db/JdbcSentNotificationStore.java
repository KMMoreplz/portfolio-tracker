package com.mikhail.telegrambot.db;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/** Журнал отправленных уведомлений в H2 (таблица sent_notifications). */
@Repository
public class JdbcSentNotificationStore implements SentNotificationStore {

    private final JdbcClient jdbcClient;

    public JdbcSentNotificationStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean markSent(long chatId, String dedupeKey) {
        try {
            jdbcClient.sql("""
                    INSERT INTO sent_notifications (chat_id, dedupe_key, sent_at)
                    VALUES (:chatId, :dedupeKey, :sentAt)
                    """)
                    .param("chatId", chatId)
                    .param("dedupeKey", dedupeKey)
                    .param("sentAt", OffsetDateTime.now())
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            // ключ уже встречался — уведомление об этом чате уже отправлялось
            return false;
        }
    }
}
