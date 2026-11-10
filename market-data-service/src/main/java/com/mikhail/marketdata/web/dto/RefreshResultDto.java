package com.mikhail.marketdata.web.dto;

import java.util.List;

/** Результат {@code POST /api/refresh}: что обновилось и по каким тикерам источник был недоступен. */
public record RefreshResultDto(
        List<String> refreshed,
        List<String> failed
) {
}
