package com.mikhail.marketdata.web;

import com.mikhail.marketdata.service.DividendService;
import com.mikhail.marketdata.web.dto.DividendsDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Дивиденды: GET /api/dividends/{ticker} — предстоящие и прошедшие выплаты. */
@RestController
@RequestMapping("/api/dividends")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @GetMapping("/{ticker}")
    public DividendsDto dividends(@PathVariable String ticker) {
        return dividendService.getDividends(TickerFormat.normalize(ticker));
    }
}
