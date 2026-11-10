package com.mikhail.telegrambot.notification;

import com.mikhail.telegrambot.portfolio.DividendItem;
import com.mikhail.telegrambot.portfolio.PortfolioMetrics;
import com.mikhail.telegrambot.portfolio.PositionValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Тексты сообщений бота. Чистая функциональная часть без зависимостей —
 * проверяется тестами напрямую.
 */
public final class MessageFormatter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int TOP_POSITIONS = 5;
    private static final int MAX_DIVIDENDS = 10;

    private MessageFormatter() {
    }

    /** Подсказка по командам — /help и /start без кода. */
    public static String help() {
        return """
                Команды:
                /start <код> — привязать чат к аккаунту (код — в веб-приложении, Настройки → «Привязать Telegram»)
                /portfolio — сводка портфеля: стоимость, P&L, крупнейшие позиции
                /dividends — ближайшие дивидендные выплаты
                /alerts on|off — включить или выключить уведомления
                /unlink — отвязать чат от аккаунта
                /help — эта подсказка

                Уведомления: изменение цены бумаги больше порога (настраивается в веб-приложении)
                и предстоящие дивиденды (за 3 дня до выплаты).""";
    }

    /** Ответ на /start без кода: как получить код привязки. */
    public static String startHelp() {
        return "Привет! Чтобы привязать чат к аккаунту, получите код в веб-приложении "
                + "(Настройки → «Привязать Telegram») и отправьте /start <код>.\n\n" + help();
    }

    public static String bound(String email, BigDecimal threshold) {
        String thresholdText = threshold == null ? "5% (по умолчанию)" : formatPercent(threshold) + "%";
        return "Чат привязан к аккаунту " + email + ".\n"
                + "Уведомления включены; порог алерта на изменение цены: " + thresholdText + ".\n"
                + "Изменить порог или отключить уведомления можно в веб-приложении или командой /alerts off.";
    }

    public static String linkInvalid() {
        return "Код привязки не подошёл: он одноразовый и действует 15 минут. "
                + "Получите новый код в веб-приложении и отправьте /start <код>.";
    }

    public static String linkUsage() {
        return "Использование: /start <код> — код из веб-приложения (шесть цифр).";
    }

    public static String unbound() {
        return "Чат отвязан от аккаунта, уведомления остановлены.";
    }

    public static String unbindNothing() {
        return "Чат и не был привязан к аккаунту.";
    }

    public static String notBound() {
        return "Чат не привязан к аккаунту. Получите код в веб-приложении "
                + "(Настройки → «Привязать Telegram») и отправьте /start <код>.";
    }

    public static String alertsState(boolean enabled) {
        return enabled ? "Уведомления включены." : "Уведомления выключены.";
    }

    /** Сводка портфеля: общая стоимость, P&L и топ позиций по стоимости. */
    public static String portfolioSummary(List<PortfolioMetrics> portfolios) {
        if (portfolios.isEmpty()) {
            return "Портфелей пока нет — создайте их в веб-приложении.";
        }
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        List<PositionValue> allPositions = new ArrayList<>();
        for (PortfolioMetrics portfolio : portfolios) {
            if (portfolio.totalValue() != null) {
                totalValue = totalValue.add(portfolio.totalValue());
            }
            if (portfolio.profit() != null) {
                totalProfit = totalProfit.add(portfolio.profit());
            }
            allPositions.addAll(portfolio.positions());
        }
        String currency = portfolios.getFirst().baseCurrency();

        StringBuilder text = new StringBuilder("Сводка портфеля\n")
                .append("Стоимость: ").append(formatAmount(totalValue)).append(' ').append(currency).append('\n')
                .append("Результат (P&L): ").append(formatSigned(totalProfit)).append(' ').append(currency);
        for (PortfolioMetrics portfolio : portfolios) {
            text.append('\n').append("— «").append(portfolio.portfolioName()).append("»: ")
                    .append(formatAmount(portfolio.totalValue())).append(' ').append(portfolio.baseCurrency())
                    .append(", P&L ").append(formatSigned(portfolio.profit()));
            if (portfolio.profitPercent() != null) {
                text.append(" (").append(formatSigned(portfolio.profitPercent())).append("%)");
            }
        }

        allPositions.sort(Comparator.comparing(
                (PositionValue p) -> p.valueInBase() == null ? BigDecimal.ZERO : p.valueInBase(),
                Comparator.reverseOrder()));
        if (!allPositions.isEmpty()) {
            text.append("\n\nКрупнейшие позиции:");
            for (PositionValue position : allPositions.stream().limit(TOP_POSITIONS).toList()) {
                text.append('\n').append("• ").append(position.ticker());
                if (position.instrumentName() != null && !position.instrumentName().isBlank()) {
                    text.append(" — ").append(position.instrumentName());
                }
                text.append(": ").append(formatAmount(position.valueInBase())).append(' ').append(currency);
                if (position.weightPercent() != null) {
                    text.append(" (").append(formatPercent(position.weightPercent())).append("% портфеля)");
                }
            }
        }
        return text.toString();
    }

    /** Ближайшие дивидендные выплаты; unavailable — источник данных недоступен. */
    public static String dividends(List<DividendItem> items, boolean marketDataAvailable) {
        if (!marketDataAvailable) {
            return "Данные о дивидендах временно недоступны, попробуйте позже.";
        }
        if (items.isEmpty()) {
            return "Ближайших дивидендных выплат нет.";
        }
        StringBuilder text = new StringBuilder("Ближайшие дивиденды:");
        for (DividendItem item : items) {
            text.append('\n').append("• ").append(item.ticker());
            if (item.paymentDate() != null) {
                text.append(" — ").append(item.paymentDate().format(DATE_FORMAT));
            }
            if (item.payout() != null) {
                text.append(": ").append(formatAmount(item.payout())).append(' ').append(item.currency());
            } else if (item.amountPerShare() != null) {
                text.append(": ").append(formatAmount(item.amountPerShare()))
                        .append(' ').append(item.currency()).append(" на бумагу");
            }
        }
        return text.toString();
    }

    /** Список дивидендов для одной сводки — не больше {@value #MAX_DIVIDENDS} строк. */
    public static List<DividendItem> nearest(List<DividendItem> items, int limit) {
        return items.stream()
                .sorted(Comparator.comparing(DividendItem::paymentDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
    }

    public static int maxDividends() {
        return MAX_DIVIDENDS;
    }

    /** Уведомление об изменении цены больше порога. */
    public static String priceAlert(PriceAlertEvent event, BigDecimal threshold) {
        String direction = event.changePercent() != null && event.changePercent().signum() < 0
                ? "упала" : "выросла";
        StringBuilder text = new StringBuilder(event.ticker()).append(": цена ").append(direction);
        if (event.changePercent() != null) {
            text.append(" на ").append(formatPercent(event.changePercent().abs())).append('%');
        }
        if (event.previousPrice() != null && event.newPrice() != null) {
            text.append(" (").append(formatAmount(event.previousPrice()))
                    .append(" → ").append(formatAmount(event.newPrice()));
            if (event.currency() != null) {
                text.append(' ').append(event.currency());
            }
            text.append(')');
        }
        text.append("\nПорог алерта: ").append(formatPercent(threshold)).append('%');
        return text.toString();
    }

    /** Напоминание о предстоящих дивидендах. */
    public static String dividendReminder(List<DividendItem> items, LocalDate today) {
        StringBuilder text = new StringBuilder("Напоминание о дивидендах (ближайшие дни):");
        for (DividendItem item : items) {
            text.append('\n').append("• ").append(item.ticker());
            if (item.paymentDate() != null) {
                long days = today.until(item.paymentDate()).getDays();
                text.append(" — выплата ").append(item.paymentDate().format(DATE_FORMAT));
                if (days == 0) {
                    text.append(" (сегодня)");
                } else if (days == 1) {
                    text.append(" (завтра)");
                } else {
                    text.append(" (через ").append(days).append(" дн.)");
                }
            }
            if (item.payout() != null) {
                text.append(": ").append(formatAmount(item.payout())).append(' ').append(item.currency());
            }
        }
        return text.toString();
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

    public static String backendUnavailable() {
        return "Сервис портфелей временно недоступен, попробуйте позже.";
    }

    public static String tokenExpired() {
        return "Связь с аккаунтом устарела. Получите новый код в веб-приложении "
                + "(Настройки → «Привязать Telegram») и отправьте /start <код>.";
    }

    public static String unknownCommand() {
        return "Неизвестная команда. Отправьте /help — список команд.";
    }

    /** Сумма без хвостовых нулей: NUMERIC(20,8) приходит как 10.00000000. */
    public static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    /** Число со знаком: +55 / -3.2. */
    public static String formatSigned(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        String value = formatAmount(amount);
        return amount.signum() > 0 ? "+" + value : value;
    }

    /** Проценты без хвостовых нулей: 5.00 → 5. */
    public static String formatPercent(BigDecimal percent) {
        return formatAmount(percent);
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
