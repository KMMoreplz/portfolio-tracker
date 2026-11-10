package com.mikhail.portfolio.web;

/** Ответ на успешную регистрацию или вход: токен для заголовка Authorization: Bearer. */
public record TokenResponseDto(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Long userId,
        String email
) {
}
