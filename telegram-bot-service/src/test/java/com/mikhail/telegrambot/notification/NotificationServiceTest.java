package com.mikhail.telegrambot.notification;

import com.mikhail.telegrambot.bot.RecordingTelegramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    private RecordingTelegramGateway gateway;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        gateway = new RecordingTelegramGateway();
        notificationService = new NotificationService(new SubscriptionStore(), gateway);
    }

    @Test
    void notifySubscribers_deliversEventTextToEverySubscribedChat() {
        notificationService.subscribe(100L, 1L);
        notificationService.subscribe(200L, 1L);
        notificationService.subscribe(300L, 2L);

        int delivered = notificationService.notifySubscribers(sberEvent());

        assertThat(delivered).isEqualTo(2);
        assertThat(gateway.messages()).hasSize(2);
        assertThat(gateway.messages())
                .allSatisfy(message -> {
                    assertThat(message.chatId()).isIn(100L, 200L);
                    assertThat(message.text()).contains("SBER");
                });
    }

    @Test
    void notifySubscribers_withoutSubscribers_sendsNothing() {
        int delivered = notificationService.notifySubscribers(sberEvent());

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void notifySubscribers_ignoresChatsSubscribedToOtherPortfolios() {
        notificationService.subscribe(100L, 2L);

        int delivered = notificationService.notifySubscribers(sberEvent());

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void unsubscribe_stopsDelivery() {
        notificationService.subscribe(100L, 1L);
        assertThat(notificationService.unsubscribe(100L, 1L)).isTrue();

        int delivered = notificationService.notifySubscribers(sberEvent());

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void subscribe_isIdempotent() {
        assertThat(notificationService.subscribe(100L, 1L)).isTrue();
        assertThat(notificationService.subscribe(100L, 1L)).isFalse();
        assertThat(notificationService.unsubscribe(100L, 1L)).isTrue();
        assertThat(notificationService.unsubscribe(100L, 1L)).isFalse();
    }

    private static PortfolioEvent sberEvent() {
        return new PortfolioEvent(EventType.POSITION_CREATED, 1L, 10L,
                "SBER", new BigDecimal("10"), new BigDecimal("310.5"), "RUB",
                OffsetDateTime.now());
    }
}
