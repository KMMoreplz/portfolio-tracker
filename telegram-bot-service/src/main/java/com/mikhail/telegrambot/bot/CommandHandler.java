package com.mikhail.telegrambot.bot;

import com.mikhail.telegrambot.config.TelegramProperties;
import com.mikhail.telegrambot.error.PortfolioNotFoundException;
import com.mikhail.telegrambot.error.PortfolioServiceUnavailableException;
import com.mikhail.telegrambot.notification.MessageFormatter;
import com.mikhail.telegrambot.notification.NotificationService;
import com.mikhail.telegrambot.portfolio.PortfolioClient;
import com.mikhail.telegrambot.portfolio.PortfolioSnapshot;
import com.mikhail.telegrambot.portfolio.PositionSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Команды бота: разбор текста сообщения и формирование ответа.
 * Сетевая отправка ответа — забота {@link UpdatePoller}; здесь только
 * логика и тексты, поэтому класс тестируется без Telegram.
 */
@Component
public class CommandHandler {

    private final NotificationService notificationService;
    private final PortfolioClient portfolioClient;

    public CommandHandler(NotificationService notificationService, PortfolioClient portfolioClient) {
        this.notificationService = notificationService;
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
            case "/start", "/help" -> MessageFormatter.help();
            case "/subscribe" -> subscribe(update.chatId(), argument);
            case "/unsubscribe" -> unsubscribe(update.chatId(), argument);
            case "/digest" -> digest(argument);
            default -> "Неизвестная команда. Отправьте /help — список команд.";
        };
    }

    /** Убирает суффикс имени бота (/subscribe@MyBot) и приводит к нижнему регистру. */
    private static String normalizeCommand(String token) {
        int atIndex = token.indexOf('@');
        String command = atIndex > 0 ? token.substring(0, atIndex) : token;
        return command.toLowerCase(Locale.ROOT);
    }

    private String subscribe(long chatId, String argument) {
        Long portfolioId = parsePortfolioId(argument);
        if (portfolioId == null) {
            return "Использование: /subscribe <id портфеля>";
        }
        PortfolioSnapshot portfolio;
        try {
            portfolio = portfolioClient.getPortfolio(portfolioId)
                    .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        } catch (PortfolioNotFoundException e) {
            return "Портфель #" + portfolioId + " не найден.";
        } catch (PortfolioServiceUnavailableException e) {
            return "Сервис портфелей недоступен, попробуйте позже.";
        }
        boolean created = notificationService.subscribe(chatId, portfolio.id());
        return created
                ? MessageFormatter.subscribed(chatId, portfolio.id())
                : MessageFormatter.alreadySubscribed(chatId, portfolio.id());
    }

    private String unsubscribe(long chatId, String argument) {
        Long portfolioId = parsePortfolioId(argument);
        if (portfolioId == null) {
            return "Использование: /unsubscribe <id портфеля>";
        }
        boolean removed = notificationService.unsubscribe(chatId, portfolioId);
        return removed
                ? MessageFormatter.unsubscribed(chatId, portfolioId)
                : MessageFormatter.notSubscribed(chatId, portfolioId);
    }

    private String digest(String argument) {
        Long portfolioId = parsePortfolioId(argument);
        if (portfolioId == null) {
            return "Использование: /digest <id портфеля>";
        }
        try {
            PortfolioSnapshot portfolio = portfolioClient.getPortfolio(portfolioId)
                    .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
            List<PositionSnapshot> positions = portfolioClient.getPositions(portfolioId);
            return MessageFormatter.formatDigest(portfolio, positions);
        } catch (PortfolioNotFoundException e) {
            return "Портфель #" + portfolioId + " не найден.";
        } catch (PortfolioServiceUnavailableException e) {
            return "Сервис портфелей недоступен, попробуйте позже.";
        }
    }

    /** @return id или null, если аргумент отсутствует или не число */
    private static Long parsePortfolioId(String argument) {
        if (argument == null || argument.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(argument);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
