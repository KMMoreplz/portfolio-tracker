package com.mikhail.telegrambot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Настройки уведомлений бота: порог алерта по умолчанию и расписание
 * напоминаний о дивидендах.
 */
@ConfigurationProperties("bot")
public record BotProperties(Alerts alerts, Reminder reminder) {

    /** Порог срабатывания алерта на изменение цены, пока пользователь не задал свой. */
    public record Alerts(BigDecimal defaultThresholdPercent) {
    }

    /**
     * Напоминания о предстоящих дивидендах.
     *
     * @param intervalMs      период проверки
     * @param initialDelayMs  задержка первого запуска после старта сервиса
     * @param lookaheadDays   за сколько дней до выплаты напоминать
     */
    public record Reminder(long intervalMs, long initialDelayMs, int lookaheadDays) {
    }
}
