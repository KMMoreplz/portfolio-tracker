package com.mikhail.telegrambot.binding;

import com.mikhail.telegrambot.db.ChatBinding;
import com.mikhail.telegrambot.db.ChatBindingStore;
import com.mikhail.telegrambot.portfolio.LinkResult;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Привязка Telegram-чата к пользователю: команда /start с одноразовым
 * кодом обменивается на связь chat id ↔ user id, которая хранится в
 * собственной маленькой БД бота.
 */
@Service
public class BindingService {

    private final ChatBindingStore store;

    public BindingService(ChatBindingStore store) {
        this.store = store;
    }

    /** Привязывает чат к пользователю; повторная привязка обновляет токен, флаг уведомлений сохраняется. */
    public ChatBinding bind(long chatId, LinkResult link) {
        boolean alertsEnabled = store.findByChatId(chatId)
                .map(ChatBinding::alertsEnabled)
                .orElse(true);
        ChatBinding binding = new ChatBinding(
                chatId, link.userId(), link.email(), link.token(), alertsEnabled, OffsetDateTime.now());
        store.save(binding);
        return binding;
    }

    /** @return true, если чат был привязан и отвязан */
    public boolean unbind(long chatId) {
        return store.deleteByChatId(chatId);
    }

    public Optional<ChatBinding> find(long chatId) {
        return store.findByChatId(chatId);
    }

    public List<ChatBinding> findAll() {
        return store.findAll();
    }

    /** @return false, если чат не привязан */
    public boolean setAlertsEnabled(long chatId, boolean enabled) {
        if (store.findByChatId(chatId).isEmpty()) {
            return false;
        }
        store.setAlertsEnabled(chatId, enabled);
        return true;
    }
}
