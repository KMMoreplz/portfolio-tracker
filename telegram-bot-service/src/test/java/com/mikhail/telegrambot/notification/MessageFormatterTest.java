package com.mikhail.telegrambot.notification;

import com.mikhail.telegrambot.portfolio.DividendItem;
import com.mikhail.telegrambot.portfolio.PortfolioMetrics;
import com.mikhail.telegrambot.portfolio.PositionValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFormatterTest {

    @Test
    void formatEvent_positionCreated_includesTickerAndQuantity() {
        PortfolioEvent event = new PortfolioEvent(EventType.POSITION_CREATED, 7L, 3L,
                "SBER", new BigDecimal("10.00000000"), new BigDecimal("310.50000000"),
                "RUB", OffsetDateTime.now());

        String text = MessageFormatter.formatEvent(event);

        assertThat(text).isEqualTo("Портфель #7: добавлена позиция SBER: 10 шт. по 310.5 RUB");
    }

    @Test
    void formatEvent_positionUpdated_withoutQuantity_formatsWithoutDetails() {
        PortfolioEvent event = new PortfolioEvent(EventType.POSITION_UPDATED, 7L, 3L,
                "SBER", null, null, null, OffsetDateTime.now());

        String text = MessageFormatter.formatEvent(event);

        assertThat(text).isEqualTo("Портфель #7: обновлена позиция SBER");
    }

    @Test
    void formatEvent_portfolioDeleted_mentionsOnlyPortfolio() {
        PortfolioEvent event = new PortfolioEvent(EventType.PORTFOLIO_DELETED, 7L,
                null, null, null, null, null, OffsetDateTime.now());

        String text = MessageFormatter.formatEvent(event);

        assertThat(text).isEqualTo("Портфель #7 удалён");
    }

    @Test
    void formatAmount_stripsTrailingZerosOfNumericColumnScale() {
        assertThat(MessageFormatter.formatAmount(new BigDecimal("10.00000000"))).isEqualTo("10");
        assertThat(MessageFormatter.formatAmount(new BigDecimal("310.50000000"))).isEqualTo("310.5");
        assertThat(MessageFormatter.formatAmount(null)).isEqualTo("—");
    }

    @Test
    void help_listsEveryCommandFromSpecification() {
        String text = MessageFormatter.help();

        assertThat(text)
                .contains("/start <код>")
                .contains("/portfolio")
                .contains("/dividends")
                .contains("/alerts on|off")
                .contains("/unlink");
    }

    @Test
    void portfolioSummary_totalsAndTopPositions() {
        PortfolioMetrics first = new PortfolioMetrics(1L, "Основной", "RUB",
                new BigDecimal("3105.00"), new BigDecimal("3050.00"),
                new BigDecimal("55.00"), new BigDecimal("1.80"),
                new BigDecimal("150.00"),
                List.of(new PositionValue("SBER", "Сбербанк", new BigDecimal("2000"), "RUB",
                        new BigDecimal("2.5"), new BigDecimal("64.41"))));
        PortfolioMetrics second = new PortfolioMetrics(2L, "Второй", "RUB",
                new BigDecimal("1000.00"), new BigDecimal("900.00"),
                new BigDecimal("100.00"), new BigDecimal("11.11"),
                BigDecimal.ZERO, List.of());

        String text = MessageFormatter.portfolioSummary(List.of(first, second));

        assertThat(text)
                .contains("Стоимость: 4105 RUB")
                .contains("Результат (P&L): +155 RUB")
                .contains("«Основной»")
                .contains("«Второй»")
                .contains("• SBER — Сбербанк: 2000 RUB (64.41% портфеля)");
    }

    @Test
    void portfolioSummary_withoutPortfolios_reportsEmpty() {
        assertThat(MessageFormatter.portfolioSummary(List.of())).contains("Портфелей пока нет");
    }

    @Test
    void dividends_nearestPayments() {
        DividendItem item = new DividendItem("SBER", "Сбербанк",
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 15),
                new BigDecimal("33"), "RUB", new BigDecimal("330"), new BigDecimal("330"));

        String text = MessageFormatter.dividends(List.of(item), true);

        assertThat(text)
                .contains("Ближайшие дивиденды")
                .contains("SBER")
                .contains("20.11.2026")
                .contains("330 RUB");
    }

    @Test
    void dividends_emptyOrUnavailable() {
        assertThat(MessageFormatter.dividends(List.of(), true))
                .contains("Ближайших дивидендных выплат нет");
        assertThat(MessageFormatter.dividends(List.of(), false))
                .contains("временно недоступны");
    }

    @Test
    void priceAlert_showsDirectionChangeAndThreshold() {
        PriceAlertEvent event = new PriceAlertEvent("SBER",
                new BigDecimal("295.6"), new BigDecimal("310.5"),
                new BigDecimal("6.2"), "RUB", null, "moex-iss");

        String text = MessageFormatter.priceAlert(event, new BigDecimal("5"));

        assertThat(text)
                .startsWith("SBER: цена выросла на 6.2%")
                .contains("295.6 → 310.5 RUB")
                .contains("Порог алерта: 5%");
    }

    @Test
    void priceAlert_negativeChange_saysFell() {
        PriceAlertEvent event = new PriceAlertEvent("GAZP",
                new BigDecimal("160"), new BigDecimal("150"),
                new BigDecimal("-6.25"), "RUB", null, null);

        String text = MessageFormatter.priceAlert(event, new BigDecimal("5"));

        assertThat(text).contains("цена упала на 6.25%");
    }

    @Test
    void dividendReminder_showsDaysUntilPayment() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        DividendItem todayItem = new DividendItem("SBER", null,
                today, null, null, "RUB", new BigDecimal("330"), null);
        DividendItem tomorrowItem = new DividendItem("GAZP", null,
                today.plusDays(1), null, null, "RUB", new BigDecimal("100"), null);
        DividendItem laterItem = new DividendItem("MGNT", null,
                today.plusDays(3), null, null, "RUB", new BigDecimal("50"), null);

        String text = MessageFormatter.dividendReminder(
                List.of(todayItem, tomorrowItem, laterItem), today);

        assertThat(text)
                .contains("Напоминание о дивидендах")
                .contains("SBER — выплата 10.11.2026 (сегодня)")
                .contains("GAZP — выплата 11.11.2026 (завтра)")
                .contains("MGNT — выплата 13.11.2026 (через 3 дн.)");
    }
}
