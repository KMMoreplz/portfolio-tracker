package com.mikhail.telegrambot.bot;

import com.mikhail.telegrambot.binding.BindingService;
import com.mikhail.telegrambot.config.BotProperties;
import com.mikhail.telegrambot.notification.NotificationService;
import com.mikhail.telegrambot.portfolio.DividendCalendar;
import com.mikhail.telegrambot.portfolio.DividendItem;
import com.mikhail.telegrambot.portfolio.LinkResult;
import com.mikhail.telegrambot.portfolio.PortfolioMetrics;
import com.mikhail.telegrambot.support.InMemoryBindingStore;
import com.mikhail.telegrambot.support.InMemorySentNotificationStore;
import com.mikhail.telegrambot.support.StubPortfolioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Команды бота: привязка по коду, сводка портфеля, дивиденды, алерты.
 * Telegram и сеть не используются — только заглушки.
 */
class CommandHandlerTest {

    private StubPortfolioClient portfolioClient;
    private BindingService bindingService;
    private CommandHandler commandHandler;

    @BeforeEach
    void setUp() {
        portfolioClient = new StubPortfolioClient();
        bindingService = new BindingService(new InMemoryBindingStore());
        commandHandler = new CommandHandler(bindingService, portfolioClient);
    }

    @Test
    void help_listsAllCommands() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/help"));

        assertThat(reply)
                .contains("/start")
                .contains("/portfolio")
                .contains("/dividends")
                .contains("/alerts")
                .contains("/unlink");
    }

    @Test
    void startWithoutCode_explainsHowToGetCode() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/start"));

        assertThat(reply).contains("Настройки").contains("/start <код>");
    }

    @Test
    void startWithValidCode_bindsChat() {
        portfolioClient.linkResult = new LinkResult(7L, "user@example.com", "jwt-token", 2592000,
                new BigDecimal("5"));

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/start 123456"));

        assertThat(reply).contains("user@example.com").contains("Уведомления включены");
        assertThat(bindingService.find(100L)).isPresent();
        assertThat(bindingService.find(100L).get().token()).isEqualTo("jwt-token");
        assertThat(bindingService.find(100L).get().userId()).isEqualTo(7L);
    }

    @Test
    void startWithInvalidCode_explains() {
        portfolioClient.linkResult = null;

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/start 123456"));

        assertThat(reply).contains("не подошёл");
        assertThat(bindingService.find(100L)).isEmpty();
    }

    @Test
    void startWithWrongArgument_showsUsage() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/start abc"));

        assertThat(reply).contains("Использование");
    }

    @Test
    void startWhenBackendDown_politelyReportsError() {
        portfolioClient.linkUnavailable = true;

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/start 123456"));

        assertThat(reply).contains("временно недоступен");
    }

    @Test
    void portfolio_withoutBinding_asksToLink() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/portfolio"));

        assertThat(reply).contains("не привязан");
    }

    @Test
    void portfolio_showsTotalValueProfitAndTopPositions() {
        bindDefault();
        portfolioClient.portfolios.clear();
        portfolioClient.metrics.clear();
        portfolioClient.addPortfolioWithTicker(1L, "SBER");
        portfolioClient.addPortfolioWithTicker(2L, "GAZP");

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/portfolio"));

        assertThat(reply)
                .contains("Стоимость: 2000 RUB")
                .contains("Результат (P&L): +200 RUB")
                .contains("Крупнейшие позиции")
                .contains("SBER")
                .contains("GAZP");
    }

    @Test
    void portfolio_whenTokenExpired_suggestsRelink() {
        bindDefault();
        portfolioClient.tokenExpired = true;

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/portfolio"));

        assertThat(reply).contains("устарела");
    }

    @Test
    void dividends_listsNearestPayments() {
        bindDefault();
        portfolioClient.portfolios.clear();
        portfolioClient.addPortfolioWithTicker(1L, "SBER");
        portfolioClient.addDividend(1L, new DividendItem("SBER", "Сбербанк",
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(2),
                new BigDecimal("33"), "RUB", new BigDecimal("330"), new BigDecimal("330")));

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/dividends"));

        assertThat(reply).contains("Ближайшие дивиденды").contains("SBER");
    }

    @Test
    void dividends_withoutPayments_reportsEmpty() {
        bindDefault();
        portfolioClient.portfolios.clear();

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/dividends"));

        assertThat(reply).contains("Ближайших дивидендных выплат нет");
    }

    @Test
    void dividends_whenMarketDataDown_reportsUnavailable() {
        bindDefault();
        portfolioClient.portfolios.clear();
        portfolioClient.addPortfolioWithTicker(1L, "SBER");
        portfolioClient.calendars.put(1L, new DividendCalendar(1L, "RUB", false, List.of()));

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/dividends"));

        assertThat(reply).contains("временно недоступны");
    }

    @Test
    void alerts_onAndOff_toggleSubscription() {
        bindDefault();

        String off = commandHandler.handle(new BotUpdate(1L, 100L, "/alerts off"));
        assertThat(off).contains("выключены");
        assertThat(bindingService.find(100L).get().alertsEnabled()).isFalse();

        String on = commandHandler.handle(new BotUpdate(1L, 100L, "/alerts on"));
        assertThat(on).contains("включены");
        assertThat(bindingService.find(100L).get().alertsEnabled()).isTrue();
    }

    @Test
    void alerts_withoutBinding_asksToLink() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/alerts on"));

        assertThat(reply).contains("не привязан");
    }

    @Test
    void unlink_removesBinding() {
        bindDefault();

        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/unlink"));

        assertThat(reply).contains("отвязан");
        assertThat(bindingService.find(100L)).isEmpty();
    }

    @Test
    void unknownCommand_pointsToHelp() {
        String reply = commandHandler.handle(new BotUpdate(1L, 100L, "/zzz"));

        assertThat(reply).contains("/help");
    }

    private void bindDefault() {
        bindingService.bind(100L, new LinkResult(7L, "user@example.com", "jwt-token", 2592000, null));
    }
}
