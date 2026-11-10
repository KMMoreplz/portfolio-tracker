package com.mikhail.portfolio.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Запрос привязки Telegram-бота: одноразовый код из веб-приложения
 * (Настройки → «Привязать Telegram»).
 */
public record LinkCodeRequestDto(
        @NotBlank(message = "код привязки обязателен")
        @Pattern(regexp = "\\d{6}", message = "код привязки — шесть цифр")
        String code
) {
}
