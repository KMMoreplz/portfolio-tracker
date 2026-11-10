package com.mikhail.portfolio.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePortfolioRequestDto(
        @NotBlank(message = "название портфеля не может быть пустым")
        @Size(max = 100, message = "название портфеля не длиннее 100 символов")
        String name,

        @Pattern(regexp = "^[A-Z]{3}$", message = "базовая валюта — трёхбуквенный код ISO 4217 в верхнем регистре")
        String baseCurrency
) {
}
