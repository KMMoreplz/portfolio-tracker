package com.mikhail.telegrambot.bot;

import com.mikhail.telegrambot.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long polling Telegram getUpdates: цикл опрашивает API с offset,
 * передаёт обновления в {@link CommandHandler} и отправляет ответы.
 *
 * <p>Без TELEGRAM_BOT_TOKEN цикл не ходит в сеть: сообщение о выключенном
 * опросе пишется в лог один раз при старте.
 */
@Component
public class UpdatePoller {

    private static final Logger log = LoggerFactory.getLogger(UpdatePoller.class);

    private final TelegramProperties properties;
    private final UpdateParser updateParser;
    private final CommandHandler commandHandler;
    private final TelegramGateway telegramGateway;
    private final RestClient restClient;

    private final AtomicBoolean disabledLogged = new AtomicBoolean(false);
    private long offset;

    public UpdatePoller(TelegramProperties properties,
                        UpdateParser updateParser,
                        CommandHandler commandHandler,
                        TelegramGateway telegramGateway,
                        RestClient telegramRestClient) {
        this.properties = properties;
        this.updateParser = updateParser;
        this.commandHandler = commandHandler;
        this.telegramGateway = telegramGateway;
        this.restClient = telegramRestClient;
    }

    @Scheduled(fixedDelayString = "${telegram.poll-interval-ms:1000}")
    public void poll() {
        if (!properties.tokenConfigured()) {
            if (disabledLogged.compareAndSet(false, true)) {
                log.info("TELEGRAM_BOT_TOKEN не задан — long polling выключен, бот не опрашивает Telegram");
            }
            return;
        }
        try {
            String body = restClient.get()
                    .uri(getUpdatesUri())
                    .retrieve()
                    .body(String.class);
            for (BotUpdate update : updateParser.parse(body)) {
                offset = Math.max(offset, update.updateId() + 1);
                handleSafely(update);
            }
        } catch (JacksonException e) {
            log.warn("Telegram вернул некорректный JSON в getUpdates");
        } catch (RuntimeException e) {
            log.warn("Не удалось получить обновления Telegram ({})", e.getClass().getSimpleName());
        }
    }

    private void handleSafely(BotUpdate update) {
        try {
            String reply = commandHandler.handle(update);
            if (reply != null && !reply.isBlank()) {
                telegramGateway.sendMessage(update.chatId(), reply);
            }
        } catch (RuntimeException e) {
            log.warn("Не удалось обработать сообщение из чата {} ({})",
                    update.chatId(), e.getClass().getSimpleName());
        }
    }

    private URI getUpdatesUri() {
        // offset подтверждает обработанные обновления, timeout — long polling на стороне Telegram
        return URI.create(properties.apiBaseUrl() + "/bot" + properties.botToken()
                + "/getUpdates?offset=" + offset + "&timeout=" + properties.pollTimeoutSeconds());
    }
}
