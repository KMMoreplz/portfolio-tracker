package com.mikhail.telegrambot.notification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище подписок в памяти сервиса. Сознательно не база данных:
 * по архитектуре проекта единственная БД принадлежит portfolio-service,
 * поэтому постоянное хранилище подписок появится вместе с таблицей
 * пользователей. Ограничение (перезапуск теряет подписки) описано в README.
 */
@Component
public class SubscriptionStore {

    private final Map<Long, Set<Long>> subscriptionsByPortfolio = new ConcurrentHashMap<>();

    /** @return true, если подписка была создана, false — уже существовала */
    public boolean subscribe(long chatId, long portfolioId) {
        return subscriptionsByPortfolio
                .computeIfAbsent(portfolioId, id -> ConcurrentHashMap.newKeySet())
                .add(chatId);
    }

    /** @return true, если подписка существовала и была удалена */
    public boolean unsubscribe(long chatId, long portfolioId) {
        Set<Long> chatIds = subscriptionsByPortfolio.get(portfolioId);
        return chatIds != null && chatIds.remove(chatId);
    }

    /** Чаты, подписанные на портфель. */
    public Set<Long> subscribersOf(long portfolioId) {
        Set<Long> chatIds = subscriptionsByPortfolio.get(portfolioId);
        return chatIds == null ? Set.of() : Set.copyOf(chatIds);
    }

    /** Портфели, на которые подписан чат. */
    public List<Long> portfolioIdsOf(long chatId) {
        return subscriptionsByPortfolio.entrySet().stream()
                .filter(entry -> entry.getValue().contains(chatId))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
