package com.mikhail.portfolio.web;

import com.mikhail.portfolio.service.DividendCalendarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Дивидендный календарь портфеля: предстоящие выплаты по текущим позициям. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
public class DividendCalendarController {

    private final DividendCalendarService dividendCalendarService;

    public DividendCalendarController(DividendCalendarService dividendCalendarService) {
        this.dividendCalendarService = dividendCalendarService;
    }

    @GetMapping("/dividend-calendar")
    public DividendCalendarDto calendar(@PathVariable Long portfolioId) {
        return dividendCalendarService.calendar(portfolioId);
    }
}
