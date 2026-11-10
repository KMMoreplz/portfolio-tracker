package com.mikhail.common.marketdata;

import java.time.Instant;

/**
 * Новостная заметка.
 *
 * @param title       заголовок
 * @param url         ссылка на материал
 * @param summary     краткое содержание или null
 * @param publishedAt время публикации или null, если лента не сообщила
 * @param feed        RSS-лента, из которой получена заметка
 */
public record NewsItem(
        String title,
        String url,
        String summary,
        Instant publishedAt,
        String feed
) {
}
