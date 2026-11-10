package com.mikhail.telegrambot.support;

import com.mikhail.telegrambot.db.ChatBinding;
import com.mikhail.telegrambot.db.ChatBindingStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Тестовое хранилище связей чат ↔ пользователь в памяти. */
public class InMemoryBindingStore implements ChatBindingStore {

    private final Map<Long, ChatBinding> bindings = new ConcurrentHashMap<>();

    @Override
    public Optional<ChatBinding> findByChatId(long chatId) {
        return Optional.ofNullable(bindings.get(chatId));
    }

    @Override
    public List<ChatBinding> findAll() {
        return new ArrayList<>(bindings.values());
    }

    @Override
    public void save(ChatBinding binding) {
        bindings.put(binding.chatId(), binding);
    }

    @Override
    public boolean deleteByChatId(long chatId) {
        return bindings.remove(chatId) != null;
    }

    @Override
    public void setAlertsEnabled(long chatId, boolean enabled) {
        ChatBinding binding = bindings.get(chatId);
        if (binding != null) {
            bindings.put(chatId, new ChatBinding(binding.chatId(), binding.userId(), binding.email(),
                    binding.token(), enabled, binding.boundAt()));
        }
    }
}
