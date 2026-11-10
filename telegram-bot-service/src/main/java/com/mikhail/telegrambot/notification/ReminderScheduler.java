package com.mikhail.telegrambot.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Расписание напоминаний о дивидендах: периодическая проверка ближайших
 * выплат по привязанным чатам. Ошибки одной проверки не останавливают
 * расписание — они логируются, следующий запуск попробует снова.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final NotificationService notificationService;

    public ReminderScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(
            fixedDelayString = "${bot.reminder.interval-ms:3600000}",
            initialDelayString = "${bot.reminder.initial-delay-ms:60000}")
    public void remind() {
        try {
            int sent = notificationService.sendDividendReminders();
            if (sent > 0) {
                log.info("Напоминания о дивидендах отправлены: {}", sent);
            }
        } catch (RuntimeException e) {
            log.warn("Проверка дивидендов не удалась ({}), следующая попытка по расписанию",
                    e.getClass().getSimpleName());
        }
    }
}
