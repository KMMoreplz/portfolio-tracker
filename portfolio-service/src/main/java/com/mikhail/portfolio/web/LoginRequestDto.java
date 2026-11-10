package com.mikhail.portfolio.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank(message = "email обязателен")
        @Email(message = "некорректный email")
        String email,

        @NotBlank(message = "пароль обязателен")
        String password
) {
}
