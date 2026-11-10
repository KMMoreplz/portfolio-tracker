package com.mikhail.telegrambot.web;

import com.mikhail.telegrambot.notification.NotificationService;
import com.mikhail.telegrambot.notification.PortfolioEvent;
import com.mikhail.telegrambot.notification.PriceAlertEvent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Приём событий портфеля от portfolio-service (REST-вебхук).
 * Контракт: POST /api/events, тело — EventRequestDto, ответ —
 * сколько чатов получило уведомление.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final NotificationService notificationService;

    public EventController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<DeliveryResultDto> receive(@Valid @RequestBody EventRequestDto dto) {
        PortfolioEvent event = new PortfolioEvent(
                dto.eventType(),
                dto.portfolioId(),
                dto.positionId(),
                dto.ticker(),
                dto.quantity(),
                dto.averagePrice(),
                dto.currency(),
                dto.occurredAt());
        int delivered = notificationService.notifyPortfolioEvent(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DeliveryResultDto(delivered));
    }

    /** Сколько подписчиков получило уведомление (0 — подписчиков нет). */
    public record DeliveryResultDto(int delivered) {
    }

    /**
     * Событие об изменении цены от market-data-service (REST-вебхук).
     * Контракт: POST /api/events/price, тело — PriceEventRequestDto,
     * ответ — сколько чатов получило алерт.
     */
    @PostMapping("/price")
    public ResponseEntity<DeliveryResultDto> receivePrice(@Valid @RequestBody PriceEventRequestDto dto) {
        PriceAlertEvent event = new PriceAlertEvent(
                dto.ticker(),
                dto.previousPrice(),
                dto.newPrice(),
                dto.changePercent(),
                dto.currency(),
                dto.quotedAt(),
                dto.source());
        int delivered = notificationService.notifyPriceChange(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DeliveryResultDto(delivered));
    }
}
