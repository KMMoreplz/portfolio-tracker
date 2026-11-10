package com.mikhail.portfolio.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePositionRequestDto(
        @NotBlank(message = "тикер не может быть пустым")
        @Pattern(regexp = "^[A-Z0-9][A-Z0-9._-]{0,31}$",
                message = "тикер — до 32 символов: заглавные буквы, цифры, точка, дефис, подчёркивание")
        String ticker,

        @Size(max = 200, message = "название инструмента не длиннее 200 символов")
        String instrumentName,

        @NotNull(message = "количество не может отсутствовать")
        @DecimalMin(value = "0", inclusive = false, message = "количество должно быть больше нуля")
        @Digits(integer = 12, fraction = 8, message = "количество: не более 12 цифр до запятой и 8 после")
        BigDecimal quantity,

        @NotNull(message = "средняя цена не может отсутствовать")
        @DecimalMin(value = "0", message = "средняя цена не может быть отрицательной")
        @Digits(integer = 12, fraction = 8, message = "средняя цена: не более 12 цифр до запятой и 8 после")
        BigDecimal averagePrice,

        @Pattern(regexp = "^[A-Z]{3}$", message = "валюта — трёхбуквенный код ISO 4217 в верхнем регистре")
        String currency
) {
}
