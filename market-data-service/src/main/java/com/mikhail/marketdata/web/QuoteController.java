package com.mikhail.marketdata.web;

import com.mikhail.marketdata.service.QuoteRefreshService;
import com.mikhail.marketdata.service.QuoteService;
import com.mikhail.marketdata.web.dto.QuoteCandleDto;
import com.mikhail.marketdata.web.dto.QuoteDto;
import com.mikhail.marketdata.web.dto.RefreshResultDto;
import com.mikhail.marketdata.web.dto.TrackTickersRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Котировки и отслеживаемые тикеры.
 * GET /api/quotes?tickers=... — котировки (fallback на сохранённые при недоступности API),
 * GET /api/quotes/{ticker} — одна котировка, GET /api/quotes/{ticker}/history — исторические свечи.
 */
@RestController
@RequestMapping("/api")
public class QuoteController {

    private static final int DEFAULT_HISTORY_DAYS = 30;

    private final QuoteService quoteService;
    private final QuoteRefreshService quoteRefreshService;

    public QuoteController(QuoteService quoteService, QuoteRefreshService quoteRefreshService) {
        this.quoteService = quoteService;
        this.quoteRefreshService = quoteRefreshService;
    }

    @GetMapping("/quotes")
    public List<QuoteDto> quotes(
            @RequestParam(name = "tickers", required = false) String tickersParam) {
        List<String> tickers = parseTickers(tickersParam);
        if (tickers.isEmpty()) {
            tickers = quoteRefreshService.trackedTickers();
        }
        return quoteService.getQuotes(tickers);
    }

    @GetMapping("/quotes/{ticker}")
    public ResponseEntity<QuoteDto> quote(@PathVariable String ticker) {
        String normalized = TickerFormat.normalize(ticker);
        return quoteService.getQuote(normalized)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TickerNotFoundException(normalized));
    }

    @GetMapping("/quotes/{ticker}/history")
    public List<QuoteCandleDto> history(
            @PathVariable String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String normalized = TickerFormat.normalize(ticker);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_HISTORY_DAYS);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("Дата from не может быть позже to");
        }
        return quoteService.getHistory(normalized, effectiveFrom, effectiveTo);
    }

    @GetMapping("/tickers")
    public List<String> tickers() {
        return quoteRefreshService.trackedTickers();
    }

    @PostMapping("/tickers")
    public ResponseEntity<List<String>> track(@RequestBody TrackTickersRequest request) {
        if (request.tickers() == null || request.tickers().isEmpty()) {
            throw new IllegalArgumentException("Список тикеров пуст");
        }
        request.tickers().forEach(TickerFormat::normalize);
        request.tickers().stream()
                .map(TickerFormat::normalize)
                .forEach(quoteRefreshService::track);
        return ResponseEntity.status(HttpStatus.CREATED).body(quoteRefreshService.trackedTickers());
    }

    @DeleteMapping("/tickers/{ticker}")
    public ResponseEntity<Void> untrack(@PathVariable String ticker) {
        String normalized = TickerFormat.normalize(ticker);
        quoteRefreshService.untrack(normalized);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResultDto> refresh() {
        RefreshResultDto result = quoteRefreshService.refreshAll();
        return ResponseEntity.accepted().body(result);
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
