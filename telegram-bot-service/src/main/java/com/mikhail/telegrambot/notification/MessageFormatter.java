package com.mikhail.telegrambot.notification;

import com.mikhail.telegrambot.portfolio.PortfolioSnapshot;
import com.mikhail.telegrambot.portfolio.PositionSnapshot;

import java.math.BigDecimal;
import java.util.List;

/**
 * Тексты сообщений бота. Чистая функциональная часть без зависимостей —
 * проверяется тестами напрямую.
 */
public final class MessageFormatter {

    private MessageFormatter() {
    }

    /** Текст уведомления о событии портфеля. */
    public static String formatEvent(PortfolioEvent event) {
        return switch (event.eventType()) {
            case PORTFOLIO_CREATED -> "Портфель #%d создан".formatted(event.portfolioId());
            case PORTFOLIO_DELETED -> "Портфель #%d удалён".formatted(event.portfolioId());
            case POSITION_CREATED -> "Портфель #%d: добавлена позиция %s%s"
                    .formatted(event.portfolioId(), event.ticker(), positionDetails(event));
            case POSITION_UPDATED -> "Портфель #%d: обновлена позиция %s%s"
                    .formatted(event.portfolioId(), event.ticker(), positionDetails(event));
            case POSITION_DELETED -> "Портфель #%d: удалена позиция %s"
                    .formatted(event.portfolioId(), event.ticker());
        };
    }

    /** Дайджест портфеля: название, валюта и позиции построчно. */
    public static String formatDigest(PortfolioSnapshot portfolio, List<PositionSnapshot> positions) {
        StringBuilder text = new StringBuilder()
                .append("Портфель «").append(portfolio.name()).append("» (#")
                .append(portfolio.id()).append(", ").append(portfolio.baseCurrency())
                .append("), позиций: ").append(positions.size());
        for (PositionSnapshot position : positions) {
            text.append('\n').append("• ").append(position.ticker());
            if (position.instrumentName() != null && !position.instrumentName().isBlank()) {
                text.append(" — ").append(position.instrumentName());
            }
            text.append(": ").append(formatAmount(position.quantity())).append(" шт. по ")
                    .append(formatAmount(position.averagePrice())).append(' ')
                    .append(position.currency());
        }
        return text.toString();
    }

    public static String subscribed(long chatId, long portfolioId) {
        return "Чат %d подписан на события портфеля #%d".formatted(chatId, portfolioId);
    }

    public static String alreadySubscribed(long chatId, long portfolioId) {
        return "Чат %d уже подписан на портфель #%d".formatted(chatId, portfolioId);
    }

    public static String unsubscribed(long chatId, long portfolioId) {
        return "Чат %d отписан от портфеля #%d".formatted(chatId, portfolioId);
    }

    public static String notSubscribed(long chatId, long portfolioId) {
        return "Чат %d не был подписан на портфель #%d".formatted(chatId, portfolioId);
    }

    public static String help() {
        return """
                Команды:
                /subscribe <id портфеля> — подписаться на события портфеля
                /unsubscribe <id портфеля> — отписаться от событий портфеля
                /digest <id портфеля> — состав портфеля
                /help — эта подсказка""";
    }

    /**
     * Сумма без хвостовых нулей: NUMERIC(20,8) приходит как 10.00000000,
     * в сообщении должно быть 10.
     */
    public static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String positionDetails(PortfolioEvent event) {
        StringBuilder details = new StringBuilder();
        if (event.quantity() != null) {
            details.append(": ").append(formatAmount(event.quantity())).append(" шт.");
        }
        if (event.averagePrice() != null) {
            details.append(" по ").append(formatAmount(event.averagePrice()));
            if (event.currency() != null) {
                details.append(' ').append(event.currency());
            }
        }
        return details.toString();
    }
}
