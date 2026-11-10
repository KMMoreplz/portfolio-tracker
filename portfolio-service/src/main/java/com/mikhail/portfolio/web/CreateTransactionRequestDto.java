package com.mikhail.portfolio.web;

import com.mikhail.common.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Создание операции. Используется и в PUT: PUT полностью заменяет операцию.
 * Поле {@code currency} необязательно — по умолчанию валюта портфеля;
 * {@code executedAt} необязательно — по умолчанию текущий момент.
 */
public record CreateTransactionRequestDto(
        @NotNull(message = "вид операции обязателен")
        TransactionType type,

        @NotBlank(message = "тикер обязателен")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$",
                message = "тикер: буквы и цифры, допустимы точка, дефис, подчёркивание (до 32 символов)")
        String ticker,

        @NotNull(message = "количество обязательно")
        @DecimalMin(value = "0", inclusive = false, message = "количество должно быть больше нуля")
        BigDecimal quantity,

        @NotNull(message = "цена обязательна")
        @DecimalMin(value = "0", message = "цена не может быть отрицательной")
        BigDecimal price,

        @DecimalMin(value = "0", message = "комиссия не может быть отрицательной")
        BigDecimal commission,

        @Pattern(regexp = "^[A-Z]{3}$",
                message = "валюта — трёхбуквенный код ISO 4217 в верхнем регистре")
        String currency,

        OffsetDateTime executedAt,

        @Size(max = 200, message = "наименование не длиннее 200 символов")
        String instrumentName
) {
}
