package com.mikhail.portfolio.error;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(OffsetDateTime timestamp, int status, String error, String message,
                       String path, List<FieldErrorDto> fieldErrors) {

    public record FieldErrorDto(String field, String message) {
    }
}
