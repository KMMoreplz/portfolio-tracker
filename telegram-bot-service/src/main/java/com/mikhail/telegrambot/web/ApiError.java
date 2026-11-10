package com.mikhail.telegrambot.web;

import java.time.OffsetDateTime;
import java.util.List;

/** Единый формат ошибок — как в portfolio-service (см. README). */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDto> fieldErrors
) {

    public record FieldErrorDto(String field, String message) {
    }
}
