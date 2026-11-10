package com.mikhail.marketdata.web.dto;

import java.time.Instant;
import java.util.List;

/** Новость по бумагам в ответе {@code GET /api/news}. */
public record NewsItemDto(
        List<String> tickers,
        String title,
        String url,
        String summary,
        Instant publishedAt,
        String feed
) {
}
