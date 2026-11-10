package com.mikhail.portfolio.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @NotBlank(message = "email обязателен")
        @Email(message = "некорректный email")
        String email,

        @NotBlank(message = "пароль обязателен")
        @Size(min = 8, max = 72, message = "пароль от 8 до 72 символов")
        String password
) {
}
