package com.mikhail.marketdata.service;

import com.mikhail.common.marketdata.Dividend;
import com.mikhail.marketdata.dividend.DividendSource;
import com.mikhail.marketdata.resilience.ExternalSourceUnavailableException;
import com.mikhail.marketdata.resilience.ResilienceExecutor;
import com.mikhail.marketdata.web.dto.DividendDto;
import com.mikhail.marketdata.web.dto.DividendsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Дивиденды: последовательный перебор источников (T-Invest API → smart-lab),
 * каждый — под ретраями и circuit breaker'ом. Если все источники недоступны —
 * ошибка 503 (обрабатывается, сервис не падает).
 */
@Service
public class DividendService {

    private static final Logger log = LoggerFactory.getLogger(DividendService.class);

    private final List<DividendSource> sources;
    private final ResilienceExecutor resilience;

    public DividendService(List<DividendSource> sources, ResilienceExecutor resilience) {
        this.sources = sources;
        this.resilience = resilience;
    }

    public DividendsDto getDividends(String ticker) {
        ExternalSourceUnavailableException lastFailure = null;
        for (DividendSource source : sources) {
            if (!source.isEnabled()) {
                continue;
            }
            try {
                List<Dividend> dividends = resilience.execute(
                        source.sourceName(), () -> source.findDividends(ticker));
                return toDto(ticker, dividends, source.sourceName());
            } catch (ExternalSourceUnavailableException exception) {
                lastFailure = exception;
                log.warn("Источник дивидендов '{}' недоступен для {}: {}",
                        source.sourceName(), ticker, exception.getMessage());
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return new DividendsDto(ticker, "none", List.of(), List.of());
    }

    private DividendsDto toDto(String ticker, List<Dividend> dividends, String sourceName) {
        LocalDate today = LocalDate.now();
        List<DividendDto> upcoming = new ArrayList<>();
        List<DividendDto> past = new ArrayList<>();
        for (Dividend dividend : dividends) {
            LocalDate reference = dividend.paymentDate() != null
                    ? dividend.paymentDate()
                    : dividend.exDate();
            DividendDto dto = toDto(dividend, reference != null && !reference.isBefore(today)
                    ? "UPCOMING"
                    : "PAST");
            if ("UPCOMING".equals(dto.status())) {
                upcoming.add(dto);
            } else {
                past.add(dto);
            }
        }
        upcoming.sort(Comparator.comparing(DividendDto::paymentDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        past.sort(Comparator.comparing(DividendDto::paymentDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new DividendsDto(ticker, sourceName, upcoming, past);
    }

    private DividendDto toDto(Dividend dividend, String status) {
        return new DividendDto(
                dividend.ticker(),
                dividend.declaredDate(),
                dividend.exDate(),
                dividend.recordDate(),
                dividend.paymentDate(),
                dividend.amountPerShare(),
                dividend.currency(),
                dividend.regularity(),
                dividend.type(),
                status,
                dividend.source());
    }
}
