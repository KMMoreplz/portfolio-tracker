package com.mikhail.portfolio.web;

import com.mikhail.portfolio.error.InvalidLinkCodeException;
import com.mikhail.portfolio.security.JwtService;
import com.mikhail.portfolio.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Привязка Telegram-бота по одноразовому коду: telegram-bot-service
 * обменивает код из веб-приложения на долгоживущий JWT пользователя.
 * Маршрут открыт без токена — секретность обеспечивает сам код
 * (шесть цифр, срок жизни 15 минут, одноразовый).
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramLinkController {

    /** Срок жизни токена, выдаваемого боту: бот работает от имени пользователя долго. */
    static final Duration BOT_TOKEN_TTL = Duration.ofDays(30);

    private final SettingsService settingsService;
    private final JwtService jwtService;

    public TelegramLinkController(SettingsService settingsService, JwtService jwtService) {
        this.settingsService = settingsService;
        this.jwtService = jwtService;
    }

    /** Погашает код: повторное использование кода и неверный код дают 401. */
    @PostMapping("/link")
    public TelegramLinkResponseDto link(@Valid @RequestBody LinkCodeRequestDto dto) {
        SettingsService.LinkGrant grant = settingsService.consumeLinkCode(dto.code())
                .orElseThrow(InvalidLinkCodeException::new);
        String token = jwtService.issueToken(grant.user().getEmail(), grant.user().getId(), BOT_TOKEN_TTL);
        return new TelegramLinkResponseDto(
                grant.user().getId(),
                grant.user().getEmail(),
                token,
                BOT_TOKEN_TTL.toSeconds(),
                grant.priceAlertThreshold());
    }
}
