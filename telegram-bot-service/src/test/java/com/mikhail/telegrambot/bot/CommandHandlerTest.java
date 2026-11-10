package com.mikhail.telegrambot.bot;

import com.mikhail.telegrambot.error.PortfolioNotFoundException;
import com.mikhail.telegrambot.error.PortfolioServiceUnavailableException;
import com.mikhail.telegrambot.notification.NotificationService;
import com.mikhail.telegrambot.notification.SubscriptionStore;
import com.mikhail.telegrambot.portfolio.PortfolioClient;
import com.mikhail.telegrambot.portfolio.PortfolioSnapshot;
import com.mikhail.telegrambot.portfolio.PositionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CommandHandlerTest {

    private SubscriptionStore subscriptionStore;
    private StubPortfolioClient portfolioClient;
    private CommandHandler commandHandler;

    @BeforeEach
    void setUp() {
        subscriptionStore = new SubscriptionStore();
        portfolioClient = new StubPortfolioClient();
        NotificationService notificationService =
                new NotificationService(subscriptionStore, (chatId, text) -> { });
        commandHandler = new CommandHandler(notificationService, portfolioClient);
    }

    @Test
    void help_returnsCommandList() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/help"));

        assertThat(reply).contains("/subscribe").contains("/digest");
    }

    @Test
    void start_returnsSameAsHelp() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/start"));

        assertThat(reply).contains("/subscribe");
    }

    @Test
    void subscribe_storesChatForExistingPortfolio() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/subscribe 1"));

        assertThat(reply).contains("подписан");
        assertThat(subscriptionStore.subscribersOf(1L)).containsExactly(100L);
    }

    @Test
    void subscribe_twice_reportsAlreadySubscribed() {
        commandHandler.handle(new BotUpdate(1L, 100L, "/subscribe 1"));

        String reply = commandHandler.handle(new BotUpdate(2L, 100L, "/subscribe 1"));

        assertThat(reply).contains("уже подписан");
    }

    @Test
    void subscribe_unknownPortfolio_returnsNotFound() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/subscribe 999"));

        assertThat(reply).isEqualTo("Портфель #999 не найден.");
        assertThat(subscriptionStore.subscribersOf(999L)).isEmpty();
    }

    @Test
    void subscribe_withoutArgument_returnsUsage() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/subscribe"));

        assertThat(reply).isEqualTo("Использование: /subscribe <id портфеля>");
    }

    @Test
    void subscribe_nonNumericArgument_returnsUsage() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/subscribe abc"));

        assertThat(reply).isEqualTo("Использование: /subscribe <id портфеля>");
    }

    @Test
    void subscribe_withBotSuffix_isAccepted() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/subscribe@MyBot 1"));

        assertThat(reply).contains("подписан");
        assertThat(subscriptionStore.subscribersOf(1L)).containsExactly(100L);
    }

    @Test
    void subscribe_portfolioServiceDown_returnsFriendlyMessage() {
        portfolioClient.failWith = new PortfolioServiceUnavailableException("down", null);

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/subscribe 1"));

        assertThat(reply).isEqualTo("Сервис портфелей недоступен, попробуйте позже.");
    }

    @Test
    void unsubscribe_reportsWhenChatWasNotSubscribed() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/unsubscribe 1"));

        assertThat(reply).contains("не был подписан");
    }

    @Test
    void digest_returnsPortfolioComposition() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/digest 1"));

        assertThat(reply)
                .contains("Портфель «Основной»")
                .contains("SBER")
                .contains("10 шт.");
    }

    @Test
    void digest_unknownPortfolio_returnsNotFound() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/digest 999"));

        assertThat(reply).isEqualTo("Портфель #999 не найден.");
    }

    @Test
    void unknownCommand_returnsHint() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/ping"));

        assertThat(reply).contains("/help");
    }

    @Test
    void emptyMessage_returnsNull() {
        assertThat(commandHandler.handle(new BotUpdate(1L, 100L, "   "))).isNull();
        assertThat(commandHandler.handle(new BotUpdate(1L, 100L, null))).isNull();
    }

    /** Заглушка portfolio-service: портфель 1 существует, остальные — нет. */
    private static final class StubPortfolioClient implements PortfolioClient {

        private RuntimeException failWith;

        @Override
        public Optional<PortfolioSnapshot> getPortfolio(long portfolioId) {
            if (failWith != null) {
                throw failWith;
            }
            return portfolioId == 1L
                    ? Optional.of(new PortfolioSnapshot(1L, "Основной", "RUB"))
                    : Optional.empty();
        }

        @Override
        public List<PositionSnapshot> getPositions(long portfolioId) {
            if (failWith != null) {
                throw failWith;
            }
            if (portfolioId != 1L) {
                throw new PortfolioNotFoundException(portfolioId);
            }
            return List.of(new PositionSnapshot("SBER", "Сбербанк",
                    new BigDecimal("10.00000000"), new BigDecimal("310.50000000"), "RUB"));
        }
    }
}
