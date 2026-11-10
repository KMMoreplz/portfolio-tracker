package com.mikhail.telegrambot.notification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
    void formatDigest_listsEveryPosition() {
        var portfolio = new com.mikhail.telegrambot.portfolio.PortfolioSnapshot(1L, "Основной", "RUB");
        var positions = List.of(
                new com.mikhail.telegrambot.portfolio.PositionSnapshot(
                        "SBER", "Сбербанк",
                        new BigDecimal("10.00000000"), new BigDecimal("310.50000000"), "RUB"),
                new com.mikhail.telegrambot.portfolio.PositionSnapshot(
                        "GAZP", null,
                        new BigDecimal("5.00000000"), new BigDecimal("160.00000000"), "RUB"));

        String text = MessageFormatter.formatDigest(portfolio, positions);

        assertThat(text)
                .startsWith("Портфель «Основной» (#1, RUB), позиций: 2")
                .contains("• SBER — Сбербанк: 10 шт. по 310.5 RUB")
                .contains("• GAZP: 5 шт. по 160 RUB");
    }

    @Test
    void help_listsAllCommands() {
        String text = MessageFormatter.help();

        assertThat(text)
                .contains("/subscribe")
                .contains("/unsubscribe")
                .contains("/digest")
                .contains("/help");
    }
}
