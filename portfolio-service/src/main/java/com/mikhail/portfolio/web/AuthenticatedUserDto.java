package com.mikhail.portfolio.web;

/** Текущий аутентифицированный пользователь (ответ GET /api/auth/me). */
public record AuthenticatedUserDto(Long id, String email) {
}
