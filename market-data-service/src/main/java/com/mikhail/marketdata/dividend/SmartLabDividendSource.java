package com.mikhail.marketdata.dividend;

import com.mikhail.common.marketdata.Dividend;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Дивиденды из публичного источника smart-lab.ru (парсинг HTML):
 * календарь выплат {@code /dividends/} (предстоящие) и история по бумаге
 * {@code /q/{ticker}/dividend/} (прошедшие). Используется как fallback,
 * когда T-Invest API недоступен или не настроен токен.
 */
@Component
public class SmartLabDividendSource implements DividendSource {

    public static final String SOURCE_NAME = "smart-lab";

    private static final Logger log = LoggerFactory.getLogger(SmartLabDividendSource.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RestClient restClient;

    public SmartLabDividendSource(RestClient smartLabRestClient) {
        this.restClient = smartLabRestClient;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<Dividend> findDividends(String ticker) {
        List<Dividend> result = new ArrayList<>();
        RuntimeException lastFailure = null;
        try {
            result.addAll(parseCalendar(fetch("/dividends/"), ticker));
        } catch (RuntimeException exception) {
            lastFailure = exception;
        }
        try {
            result.addAll(parseHistory(fetch("/q/" + ticker + "/dividend/"), ticker));
        } catch (RuntimeException exception) {
            lastFailure = exception;
        }
        if (result.isEmpty() && lastFailure != null) {
            throw lastFailure;
        }
        return result;
    }

    private String fetch(String path) {
        return restClient.get()
                .uri(path)
                .retrieve()
                .body(String.class);
    }

    /** Календарь: [название, тикер, период, дивиденд, доходность, СД, купить до, реестр, выплата, цена]. */
    private List<Dividend> parseCalendar(String html, String ticker) {
        List<Dividend> result = new ArrayList<>();
        Document document = Jsoup.parse(html);
        for (Element row : tableRows(document, "table.dividends")) {
            Elements cells = row.select("td");
            if (cells.size() < 9 || !ticker.equalsIgnoreCase(cells.get(1).text().trim())) {
                continue;
            }
            result.add(new Dividend(
                    ticker,
                    null,
                    date(cells.get(6)),
                    date(cells.get(7)),
                    date(cells.get(8)),
                    amount(cells.get(3)),
                    "RUB",
                    null,
                    null,
                    SOURCE_NAME));
        }
        return result;
    }

    /** История: [тикер, дата T-1, дата отсечки, период, дивиденд, цена, доходность]. */
    private List<Dividend> parseHistory(String html, String ticker) {
        List<Dividend> result = new ArrayList<>();
        Document document = Jsoup.parse(html);
        for (Element row : tableRows(document, "table.dividends")) {
            Elements cells = row.select("td");
            if (cells.size() < 5 || !ticker.equalsIgnoreCase(cells.get(0).text().trim())) {
                continue;
            }
            result.add(new Dividend(
                    ticker,
                    null,
                    date(cells.get(1)),
                    date(cells.get(2)),
                    null,
                    amount(cells.get(4)),
                    "RUB",
                    null,
                    null,
                    SOURCE_NAME));
        }
        return result;
    }

    private Elements tableRows(Document document, String cssQuery) {
        Elements rows = new Elements();
        for (Element table : document.select(cssQuery)) {
            rows.addAll(table.select("tr"));
        }
        return rows;
    }

    private LocalDate date(Element cell) {
        String text = cell.text().trim();
        try {
            return LocalDate.parse(text, DATE_FORMAT);
        } catch (RuntimeException exception) {
            log.debug("Не удалось разобрать дату '{}' в smart-lab", text);
            return null;
        }
    }

    private BigDecimal amount(Element cell) {
        String cleaned = cell.text()
                .replaceAll("[^0-9,.-]", "")
                .replace(',', '.')
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException exception) {
            log.debug("Не удалось разобрать сумму '{}' в smart-lab", cell.text());
            return null;
        }
    }
}
