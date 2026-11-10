package com.mikhail.portfolio.web;

import com.mikhail.portfolio.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Настройки текущего пользователя: порог алертов на изменение цены
 * и одноразовый код привязки Telegram-бота. Все маршруты за JWT.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsDto get(@AuthenticationPrincipal Jwt jwt) {
        return settingsService.get(settingsService.requireUser(jwt.getSubject()));
    }

    @PutMapping
    public SettingsDto update(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody UpdateSettingsRequestDto dto) {
        return settingsService.update(settingsService.requireUser(jwt.getSubject()), dto);
    }

    /** Выдаёт одноразовый код: повторный вызов заменяет прежний код новым. */
    @PostMapping("/telegram/link-code")
    public SettingsDto generateLinkCode(@AuthenticationPrincipal Jwt jwt) {
        return settingsService.generateLinkCode(settingsService.requireUser(jwt.getSubject()));
    }
}
