package com.mikhail.portfolio.web;

import com.mikhail.portfolio.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Текущая оценка портфеля: стоимость, P&L, доходность, доли позиций, дивиденды. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public MetricsDto metrics(@PathVariable Long portfolioId) {
        return metricsService.metrics(portfolioId);
    }
}
