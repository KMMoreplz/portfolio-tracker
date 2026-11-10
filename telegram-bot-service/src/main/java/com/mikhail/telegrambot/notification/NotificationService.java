package com.mikhail.telegrambot.notification;

import com.mikhail.telegrambot.bot.TelegramGateway;
import com.mikhail.telegrambot.config.BotProperties;
import com.mikhail.telegrambot.db.ChatBinding;
import com.mikhail.telegrambot.db.ChatBindingStore;
import com.mikhail.telegrambot.db.SentNotificationStore;
import com.mikhail.telegrambot.error.PortfolioServiceUnavailableException;
import com.mikhail.telegrambot.error.TokenExpiredException;
import com.mikhail.telegrambot.portfolio.DividendCalendar;
import com.mikhail.telegrambot.portfolio.DividendItem;
import com.mikhail.telegrambot.portfolio.PortfolioClient;
import com.mikhail.telegrambot.portfolio.PortfolioInfo;
import com.mikhail.telegrambot.portfolio.PortfolioMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Уведомления в чаты: события портфеля, алерты на изменение цены
 * (market-data-service, REST-вебхук) и напоминания о дивидендах.
 *
 * <p>Рассылка идёт только привязанным чатам с включёнными уведомлениями.
 * Данные портфеля и настройки бот читает из portfolio-service по
 * сохранённому токену пользователя; свои бизнес-данные не хранит.
 * Ошибки backend'а не рвут обработку: чат пропускается с записью в лог.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ChatBindingStore bindingStore;
    private final SentNotificationStore sentNotificationStore;
    private final PortfolioClient portfolioClient;
    private final TelegramGateway telegramGateway;
    private final BotProperties botProperties;

    public NotificationService(ChatBindingStore bindingStore,
                               SentNotificationStore sentNotificationStore,
                               PortfolioClient portfolioClient,
                               TelegramGateway telegramGateway,
                               BotProperties botProperties) {
        this.bindingStore = bindingStore;
        this.sentNotificationStore = sentNotificationStore;
        this.portfolioClient = portfolioClient;
        this.telegramGateway = telegramGateway;
        this.botProperties = botProperties;
    }

    /**
     * Рассылает событие портфеля всем привязанным чатам с включёнными уведомлениями.
     *
     * @return количество чатов, получивших сообщение
     */
    public int notifyPortfolioEvent(PortfolioEvent event) {
        String text = MessageFormatter.formatEvent(event);
        int delivered = 0;
        for (ChatBinding binding : alertsEnabled()) {
            telegramGateway.sendMessage(binding.chatId(), text);
            delivered++;
        }
        if (delivered > 0) {
            log.info("Событие {} портфеля {} отправлено в {} чат(ов)",
                    event.eventType(), event.portfolioId(), delivered);
        }
        return delivered;
    }

    /**
     * Алерт на изменение цены: отправляет чатам, у которых бумага есть в
     * портфеле, а изменение превысило порог алерта пользователя (или порог
     * по умолчанию, если свой не задан).
     *
     * @return количество чатов, получивших сообщение
     */
    public int notifyPriceChange(PriceAlertEvent event) {
        if (event.ticker() == null || event.changePercent() == null) {
            return 0;
        }
        int delivered = 0;
        for (ChatBinding binding : alertsEnabled()) {
            try {
                BigDecimal threshold = portfolioClient.priceAlertThreshold(binding.token())
                        .orElse(botProperties.alerts().defaultThresholdPercent());
                if (event.changePercent().abs().compareTo(threshold) < 0) {
                    continue;
                }
                if (!heldTickers(binding).contains(event.ticker())) {
                    continue;
                }
                telegramGateway.sendMessage(binding.chatId(), MessageFormatter.priceAlert(event, threshold));
                delivered++;
            } catch (TokenExpiredException e) {
                log.warn("Чат {}: токен устарел, алерт {} пропущен", binding.chatId(), event.ticker());
            } catch (PortfolioServiceUnavailableException e) {
                log.warn("Чат {}: portfolio-service недоступен, алерт {} пропущен",
                        binding.chatId(), event.ticker());
            }
        }
        return delivered;
    }

    /**
     * Напоминания о предстоящих дивидендах: за {@code bot.reminder.lookahead-days}
     * дней до выплаты. Каждое напоминание отправляется чату один раз
     * (журнал sent_notifications).
     *
     * @return количество отправленных напоминаний
     */
    public int sendDividendReminders() {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(botProperties.reminder().lookaheadDays());
        int delivered = 0;
        for (ChatBinding binding : alertsEnabled()) {
            try {
                List<DividendItem> upcoming = upcomingDividends(binding.token(), today, until);
                List<DividendItem> fresh = new ArrayList<>();
                for (DividendItem item : upcoming) {
                    String key = "DIV:%s:%s".formatted(item.ticker(), item.paymentDate());
                    if (sentNotificationStore.markSent(binding.chatId(), key)) {
                        fresh.add(item);
                    }
                }
                if (!fresh.isEmpty()) {
                    telegramGateway.sendMessage(binding.chatId(),
                            MessageFormatter.dividendReminder(fresh, today));
                    delivered += fresh.size();
                }
            } catch (TokenExpiredException e) {
                log.warn("Чат {}: токен устарел, напоминания о дивидендах пропущены", binding.chatId());
            } catch (PortfolioServiceUnavailableException e) {
                log.warn("Чат {}: portfolio-service недоступен, напоминания о дивидендах пропущены",
                        binding.chatId());
            }
        }
        return delivered;
    }

    /** Дивидендные выплаты по всем портфелям в интервале [today, until]. */
    List<DividendItem> upcomingDividends(String token, LocalDate today, LocalDate until) {
        List<DividendItem> upcoming = new ArrayList<>();
        for (PortfolioInfo portfolio : portfolioClient.listPortfolios(token)) {
            DividendCalendar calendar = portfolioClient.dividendCalendar(token, portfolio.id());
            for (DividendItem item : calendar.items()) {
                if (item.paymentDate() != null
                        && !item.paymentDate().isBefore(today)
                        && !item.paymentDate().isAfter(until)) {
                    upcoming.add(item);
                }
            }
        }
        return upcoming;
    }

    /** Тикеры, которые есть в портфелях пользователя. */
    private Set<String> heldTickers(ChatBinding binding) {
        Set<String> tickers = new HashSet<>();
        for (PortfolioInfo portfolio : portfolioClient.listPortfolios(binding.token())) {
            Optional<PortfolioMetrics> metrics = portfolioClient.metrics(binding.token(), portfolio.id());
            metrics.ifPresent(value -> value.positions().forEach(p -> tickers.add(p.ticker())));
        }
        return tickers;
    }

    private List<ChatBinding> alertsEnabled() {
        return bindingStore.findAll().stream().filter(ChatBinding::alertsEnabled).toList();
    }
}
