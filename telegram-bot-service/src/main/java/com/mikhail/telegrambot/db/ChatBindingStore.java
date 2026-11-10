package com.mikhail.telegrambot.db;

import java.util.List;
import java.util.Optional;

/**
 * Хранилище связей чат ↔ пользователь. Отдельный интерфейс, чтобы
 * бизнес-логика бота тестировалась на памяти без базы данных.
 */
public interface ChatBindingStore {

    Optional<ChatBinding> findByChatId(long chatId);

    List<ChatBinding> findAll();

    /** Создаёт или заменяет связь чата (повторная привязка обновляет токен). */
    void save(ChatBinding binding);

    /** @return true, если связь существовала и была удалена */
    boolean deleteByChatId(long chatId);

    void setAlertsEnabled(long chatId, boolean enabled);
}
