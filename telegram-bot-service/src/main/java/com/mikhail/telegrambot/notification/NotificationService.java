package com.mikhail.telegrambot.notification;

import com.mikhail.telegrambot.bot.TelegramGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Бизнес-логика уведомлений: событие портфеля -> подписанные чаты ->
 * текст сообщения -> отправка через {@link TelegramGateway}.
 *
 * <p>Если на портфель нет подписчиков, сообщения не формируются и
 * сеть не трогается. Отправка в Telegram отдельного подтверждения
 * не требует: ошибки логируются в gateway и не рвут обработку события.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SubscriptionStore subscriptionStore;
    private final TelegramGateway telegramGateway;

    public NotificationService(SubscriptionStore subscriptionStore, TelegramGateway telegramGateway) {
        this.subscriptionStore = subscriptionStore;
        this.telegramGateway = telegramGateway;
    }

    /**
     * Рассылает событие всем чатам, подписанным на портфель.
     *
     * @return количество чатов, получивших сообщение
     */
    public int notifySubscribers(PortfolioEvent event) {
        Set<Long> chatIds = subscriptionStore.subscribersOf(event.portfolioId());
        if (chatIds.isEmpty()) {
            log.info("На портфель {} нет подписчиков, событие {} пропущено",
                    event.portfolioId(), event.eventType());
            return 0;
        }
        String text = MessageFormatter.formatEvent(event);
        for (long chatId : chatIds) {
            telegramGateway.sendMessage(chatId, text);
        }
        log.info("Событие {} для портфеля {} отправлено в {} чат(ов)",
                event.eventType(), event.portfolioId(), chatIds.size());
        return chatIds.size();
    }

    /** Подписка чата на портфель. @return false, если подписка уже была */
    public boolean subscribe(long chatId, long portfolioId) {
        return subscriptionStore.subscribe(chatId, portfolioId);
    }

    /** Отписка чата от портфеля. @return false, если подписки не было */
    public boolean unsubscribe(long chatId, long portfolioId) {
        return subscriptionStore.unsubscribe(chatId, portfolioId);
    }
}
