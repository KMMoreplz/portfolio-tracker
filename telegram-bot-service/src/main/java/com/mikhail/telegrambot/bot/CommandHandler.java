package com.mikhail.telegrambot.bot;

import com.mikhail.telegrambot.binding.BindingService;
import com.mikhail.telegrambot.db.ChatBinding;
import com.mikhail.telegrambot.error.PortfolioServiceUnavailableException;
import com.mikhail.telegrambot.error.TokenExpiredException;
import com.mikhail.telegrambot.notification.MessageFormatter;
import com.mikhail.telegrambot.portfolio.DividendCalendar;
import com.mikhail.telegrambot.portfolio.DividendItem;
import com.mikhail.telegrambot.portfolio.LinkResult;
import com.mikhail.telegrambot.portfolio.PortfolioClient;
import com.mikhail.telegrambot.portfolio.PortfolioInfo;
import com.mikhail.telegrambot.portfolio.PortfolioMetrics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Команды бота: разбор текста сообщения и формирование ответа.
 * Сетевая отправка ответа — забота {@link UpdatePoller}; здесь только
 * логика и тексты, поэтому класс тестируется без Telegram.
 *
 * <p>Команды /portfolio и /dividends читают данные из portfolio-service
 * по токену привязанного пользователя; при недоступности backend'а
 * пользователь получает вежливое сообщение об ошибке.
 */
@Component
public class CommandHandler {

    private static final String LINK_CODE_PATTERN = "\\d{6}";

    private final BindingService bindingService;
    private final PortfolioClient portfolioClient;

    public CommandHandler(BindingService bindingService, PortfolioClient portfolioClient) {
        this.bindingService = bindingService;
        this.portfolioClient = portfolioClient;
    }

    /** @return ответ в чат; null или пустая строка — отвечать нечего */
    public String handle(BotUpdate update) {
        String text = update.text() == null ? "" : update.text().trim();
        if (text.isEmpty()) {
            return null;
        }
        String[] parts = text.split("\\s+");
        String command = normalizeCommand(parts[0]);
        String argument = parts.length > 1 ? parts[1] : null;
        return switch (command) {
            case "/start" -> start(update.chatId(), argument);
            case "/help" -> MessageFormatter.help();
            case "/portfolio" -> portfolio(update.chatId());
            case "/dividends" -> dividends(update.chatId());
            case "/alerts" -> alerts(update.chatId(), argument);
            case "/unlink" -> unlink(update.chatId());
            default -> MessageFormatter.unknownCommand();
        };
    }

    /** Убирает суффикс имени бота (/start@MyBot) и приводит к нижнему регистру. */
    private static String normalizeCommand(String token) {
        int atIndex = token.indexOf('@');
        String command = atIndex > 0 ? token.substring(0, atIndex) : token;
        return command.toLowerCase(Locale.ROOT);
    }

    private String start(long chatId, String argument) {
        if (argument == null) {
            return MessageFormatter.startHelp();
        }
        if (!argument.matches(LINK_CODE_PATTERN)) {
            return MessageFormatter.linkUsage();
        }
        try {
            Optional<LinkResult> link = portfolioClient.link(argument);
            if (link.isEmpty()) {
                return MessageFormatter.linkInvalid();
            }
            LinkResult result = link.get();
            bindingService.bind(chatId, result);
            return MessageFormatter.bound(result.email(), result.priceAlertThreshold());
        } catch (PortfolioServiceUnavailableException e) {
            return MessageFormatter.backendUnavailable();
        }
    }

    private String portfolio(long chatId) {
        Optional<ChatBinding> binding = bindingService.find(chatId);
        if (binding.isEmpty()) {
            return MessageFormatter.notBound();
        }
        try {
            String token = binding.get().token();
            List<PortfolioMetrics> metrics = new ArrayList<>();
            for (PortfolioInfo portfolio : portfolioClient.listPortfolios(token)) {
                portfolioClient.metrics(token, portfolio.id()).ifPresent(metrics::add);
            }
            return MessageFormatter.portfolioSummary(metrics);
        } catch (TokenExpiredException e) {
            return MessageFormatter.tokenExpired();
        } catch (PortfolioServiceUnavailableException e) {
            return MessageFormatter.backendUnavailable();
        }
    }

    private String dividends(long chatId) {
        Optional<ChatBinding> binding = bindingService.find(chatId);
        if (binding.isEmpty()) {
            return MessageFormatter.notBound();
        }
        try {
            String token = binding.get().token();
            List<DividendItem> items = new ArrayList<>();
            boolean marketDataAvailable = true;
            for (PortfolioInfo portfolio : portfolioClient.listPortfolios(token)) {
                DividendCalendar calendar = portfolioClient.dividendCalendar(token, portfolio.id());
                // если источник данных по любому портфелю недоступен — ответ честно помечается
                marketDataAvailable = marketDataAvailable && calendar.marketDataAvailable();
                items.addAll(calendar.items());
            }
            return MessageFormatter.dividends(
                    MessageFormatter.nearest(items, MessageFormatter.maxDividends()),
                    marketDataAvailable);
        } catch (TokenExpiredException e) {
            return MessageFormatter.tokenExpired();
        } catch (PortfolioServiceUnavailableException e) {
            return MessageFormatter.backendUnavailable();
        }
    }

    private String alerts(long chatId, String argument) {
        Boolean enabled = parseAlertsArgument(argument);
        if (enabled == null) {
            return "Использование: /alerts on или /alerts off";
        }
        if (!bindingService.setAlertsEnabled(chatId, enabled)) {
            return MessageFormatter.notBound();
        }
        return MessageFormatter.alertsState(enabled);
    }

    /** @return true/false для on/off; null — аргумент не распознан */
    private static Boolean parseAlertsArgument(String argument) {
        if (argument == null) {
            return null;
        }
        return switch (argument.toLowerCase(Locale.ROOT)) {
            case "on", "включить" -> Boolean.TRUE;
            case "off", "выключить" -> Boolean.FALSE;
            default -> null;
        };
    }

    private String unlink(long chatId) {
        return bindingService.unbind(chatId)
                ? MessageFormatter.unbound()
                : MessageFormatter.unbindNothing();
    }
}
