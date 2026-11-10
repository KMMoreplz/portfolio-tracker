package com.mikhail.marketdata.web;

import com.mikhail.marketdata.service.NewsService;
import com.mikhail.marketdata.web.dto.NewsItemDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Новости: GET /api/news?tickers=SBER,GAZP — агрегация из RSS по бумагам. */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public List<NewsItemDto> news(
            @RequestParam(name = "tickers", required = false) String tickersParam,
            @RequestParam(required = false) Integer limit) {
        List<String> tickers = parseTickers(tickersParam);
        return newsService.getNews(tickers, limit);
    }

    private List<String> parseTickers(String tickersParam) {
        if (tickersParam == null || tickersParam.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tickersParam.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(TickerFormat::normalize)
                .toList();
    }
}
