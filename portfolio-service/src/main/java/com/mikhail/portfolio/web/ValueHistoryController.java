package com.mikhail.portfolio.web;

import com.mikhail.portfolio.service.ValueHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** История стоимости портфеля по дням — данные для графика на дашборде. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
public class ValueHistoryController {

    private final ValueHistoryService valueHistoryService;

    public ValueHistoryController(ValueHistoryService valueHistoryService) {
        this.valueHistoryService = valueHistoryService;
    }

    /**
     * Точки графика за период. days ограничивается 1…365 и по умолчанию равен 30;
     * выход за границы не ошибка запроса, а ограничение источника.
     */
    @GetMapping("/value-history")
    public ValueHistoryDto history(@PathVariable Long portfolioId,
                                   @RequestParam(required = false) Integer days) {
        return valueHistoryService.history(portfolioId, days);
    }
}
