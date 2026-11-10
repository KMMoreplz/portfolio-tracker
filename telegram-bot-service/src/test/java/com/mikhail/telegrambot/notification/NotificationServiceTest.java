package com.mikhail.telegrambot.notification;

import com.mikhail.telegrambot.bot.RecordingTelegramGateway;
import com.mikhail.telegrambot.config.BotProperties;
import com.mikhail.telegrambot.db.ChatBinding;
import com.mikhail.telegrambot.portfolio.DividendItem;
import com.mikhail.telegrambot.portfolio.LinkResult;
import com.mikhail.telegrambot.support.InMemoryBindingStore;
import com.mikhail.telegrambot.support.InMemorySentNotificationStore;
import com.mikhail.telegrambot.support.StubPortfolioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Уведомления: события портфеля, алерты на изменение цены с порогом и
 * фильтром по бумагам портфеля, напоминания о дивидендах с дедупликацией.
 */
class NotificationServiceTest {

    private InMemoryBindingStore bindingStore;
    private StubPortfolioClient portfolioClient;
    private RecordingTelegramGateway gateway;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        bindingStore = new InMemoryBindingStore();
        portfolioClient = new StubPortfolioClient();
        gateway = new RecordingTelegramGateway();
        BotProperties properties = new BotProperties(
                new BotProperties.Alerts(new BigDecimal("5")),
                new BotProperties.Reminder(3600000, 60000, 3));
        notificationService = new NotificationService(bindingStore, new InMemorySentNotificationStore(),
                portfolioClient, gateway, properties);
    }

    @Test
    void portfolioEvent_deliversToEveryBoundChatWithAlertsOn() {
        bind(100L);
        bind(200L);
        bindingStore.save(new ChatBinding(300L, 7L, "user@example.com", "jwt", false,
                OffsetDateTime.now()));

        int delivered = notificationService.notifyPortfolioEvent(sberEvent());

        assertThat(delivered).isEqualTo(2);
        assertThat(gateway.messages()).hasSize(2);
        assertThat(gateway.messages())
                .allSatisfy(message -> {
                    assertThat(message.chatId()).isIn(100L, 200L);
                    assertThat(message.text()).contains("SBER");
                });
    }

    @Test
    void portfolioEvent_withoutBindings_sendsNothing() {
        int delivered = notificationService.notifyPortfolioEvent(sberEvent());

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void priceChange_aboveThresholdAndHeld_deliversAlert() {
        bind(100L);
        portfolioClient.addPortfolioWithTicker(1L, "SBER");

        int delivered = notificationService.notifyPriceChange(sberPriceChange(new BigDecimal("6.2")));

        assertThat(delivered).isEqualTo(1);
        assertThat(gateway.messages()).hasSize(1);
        assertThat(gateway.messages().getFirst().text())
                .contains("SBER")
                .contains("выросла")
                .contains("Порог алерта: 5%");
    }

    @Test
    void priceChange_belowThreshold_sendsNothing() {
        bind(100L);
        portfolioClient.addPortfolioWithTicker(1L, "SBER");

        int delivered = notificationService.notifyPriceChange(sberPriceChange(new BigDecimal("2")));

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void priceChange_usesUserThresholdFromSettings() {
        bind(100L);
        portfolioClient.addPortfolioWithTicker(1L, "SBER");
        portfolioClient.threshold = new BigDecimal("10");

        int delivered = notificationService.notifyPriceChange(sberPriceChange(new BigDecimal("6.2")));

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void priceChange_tickerNotInPortfolio_sendsNothing() {
        bind(100L);
        portfolioClient.addPortfolioWithTicker(1L, "GAZP");

        int delivered = notificationService.notifyPriceChange(sberPriceChange(new BigDecimal("6.2")));

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void priceChange_whenBackendDown_skipsChatQuietly() {
        bind(100L);
        portfolioClient.unavailable = true;

        int delivered = notificationService.notifyPriceChange(sberPriceChange(new BigDecimal("6.2")));

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void dividendReminders_sendsOncePerPayment() {
        bind(100L);
        portfolioClient.addPortfolioWithTicker(1L, "SBER");
        portfolioClient.addDividend(1L, new DividendItem("SBER", "Сбербанк",
                LocalDate.now().plusDays(2), LocalDate.now(),
                new BigDecimal("33"), "RUB", new BigDecimal("330"), new BigDecimal("330")));

        int first = notificationService.sendDividendReminders();
        int second = notificationService.sendDividendReminders();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(gateway.messages()).hasSize(1);
        assertThat(gateway.messages().getFirst().text()).contains("Напоминание о дивидендах");
    }

    @Test
    void dividendReminders_skipsPaymentsFarInTheFuture() {
        bind(100L);
        portfolioClient.addPortfolioWithTicker(1L, "SBER");
        portfolioClient.addDividend(1L, new DividendItem("SBER", "Сбербанк",
                LocalDate.now().plusDays(10), LocalDate.now(),
                new BigDecimal("33"), "RUB", new BigDecimal("330"), new BigDecimal("330")));

        int delivered = notificationService.sendDividendReminders();

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    @Test
    void dividendReminders_respectsAlertsToggle() {
        bind(100L);
        bindingStore.setAlertsEnabled(100L, false);
        portfolioClient.addPortfolioWithTicker(1L, "SBER");
        portfolioClient.addDividend(1L, new DividendItem("SBER", "Сбербанк",
                LocalDate.now().plusDays(1), LocalDate.now(),
                new BigDecimal("33"), "RUB", new BigDecimal("330"), new BigDecimal("330")));

        int delivered = notificationService.sendDividendReminders();

        assertThat(delivered).isZero();
        assertThat(gateway.messages()).isEmpty();
    }

    private void bind(long chatId) {
        bindingStore.save(new ChatBinding(chatId, 7L, "user@example.com", "jwt", true,
                OffsetDateTime.now()));
    }

    private static PortfolioEvent sberEvent() {
        return new PortfolioEvent(EventType.POSITION_CREATED, 1L, 10L,
                "SBER", new BigDecimal("10"), new BigDecimal("310.5"), "RUB",
                OffsetDateTime.now());
    }

    private static PriceAlertEvent sberPriceChange(BigDecimal changePercent) {
        return new PriceAlertEvent("SBER", new BigDecimal("295.6"), new BigDecimal("310.5"),
                changePercent, "RUB", java.time.Instant.now(), "moex-iss");
    }
}
