package com.mikhail.marketdata.service;

import com.mikhail.common.marketdata.InstrumentInfo;
import com.mikhail.common.marketdata.MarketDataProvider;
import com.mikhail.common.marketdata.NewsItem;
import com.mikhail.marketdata.config.MarketDataProperties;
import com.mikhail.marketdata.repository.PortfolioPositionTickerRepository;
import com.mikhail.marketdata.repository.TrackedTickerRepository;
import com.mikhail.marketdata.resilience.ExternalSourceUnavailableException;
import com.mikhail.marketdata.resilience.ResilienceExecutor;
import com.mikhail.marketdata.web.dto.NewsItemDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Агрегация новостей по бумагам из RSS-лент: заметки фильтруются по тикеру
 * и краткому наименованию инструмента. Ленты кэшируются на короткое время,
 * чтобы не долбить источники на каждый запрос.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final MarketDataProperties properties;
    private final RestClient newsRestClient;
    private final ResilienceExecutor resilience;
    private final List<MarketDataProvider> providers;
    private final TrackedTickerRepository trackedTickerRepository;
    private final PortfolioPositionTickerRepository positionTickerRepository;
    private final Map<String, CachedFeed> feedCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> keywordCache = new ConcurrentHashMap<>();

    public NewsService(
            MarketDataProperties properties,
            RestClient newsRestClient,
            ResilienceExecutor resilience,
            List<MarketDataProvider> providers,
            TrackedTickerRepository trackedTickerRepository,
            PortfolioPositionTickerRepository positionTickerRepository) {
        this.properties = properties;
        this.newsRestClient = newsRestClient;
        this.resilience = resilience;
        this.providers = providers;
        this.trackedTickerRepository = trackedTickerRepository;
        this.positionTickerRepository = positionTickerRepository;
    }

    /**
     * Новости по тикерам; если тикеры не переданы — по всем отслеживаемым.
     *
     * @throws ExternalSourceUnavailableException если ни одна лента недоступна
     */
    public List<NewsItemDto> getNews(List<String> requestedTickers, Integer limit) {
        List<String> tickers = requestedTickers.isEmpty() ? trackedTickers() : requestedTickers;
        Map<String, Set<String>> keywords = keywordsFor(tickers);
        List<String> feeds = properties.news().feeds();
        int failures = 0;
        List<NewsItemDto> matched = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (String feed : feeds) {
            try {
                List<NewsItem> items = resilience.execute("news-rss", () -> fetchFeedCached(feed));
                for (NewsItem item : items) {
                    if (item.url() != null && !seenUrls.add(item.url())) {
                        continue;
                    }
                    List<String> matchedTickers = matchedTickers(item, keywords);
                    if (!matchedTickers.isEmpty()) {
                        matched.add(toDto(item, matchedTickers));
                    }
                }
            } catch (ExternalSourceUnavailableException exception) {
                failures++;
                log.warn("RSS-лента недоступна: {} — {}", feed, exception.getMessage());
            }
        }
        if (!feeds.isEmpty() && failures == feeds.size()) {
            throw new ExternalSourceUnavailableException("news-rss", null);
        }
        matched.sort(Comparator.comparing(
                NewsItemDto::publishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int effectiveLimit = limit != null && limit > 0
                ? Math.min(limit, 100)
                : properties.news().limit();
        return matched.stream().limit(effectiveLimit).toList();
    }

    private List<String> trackedTickers() {
        Set<String> tickers = new LinkedHashSet<>();
        if (properties.tickers() != null) {
            properties.tickers().forEach(ticker -> tickers.add(ticker.trim().toUpperCase(Locale.ROOT)));
        }
        tickers.addAll(trackedTickerRepository.findAll());
        tickers.addAll(positionTickerRepository.findTickers());
        return tickers.stream().sorted().toList();
    }

    /** Ключевые слова для тикера: сам тикер и слова краткого наименования инструмента. */
    private Map<String, Set<String>> keywordsFor(List<String> tickers) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String ticker : tickers) {
            result.computeIfAbsent(ticker, key -> {
                Set<String> words = new LinkedHashSet<>();
                words.add(key.toLowerCase(Locale.ROOT));
                instrumentName(key).ifPresent(name -> addNameWords(name, words));
                return words;
            });
        }
        return result;
    }

    /**
     * Краткое имя вида «Магнит ао» или «Т-Технологии» не встречается в новостях
     * целиком («акции Магнита», «Т-Технологий»), поэтому имя разбивается на слова,
     * а для слов длиннее шести символов добавляется основа — она совпадает
     * при любом падежном окончании.
     */
    private static void addNameWords(String name, Set<String> words) {
        for (String word : name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() < 3) {
                continue;
            }
            words.add(word);
            if (word.length() > 6) {
                words.add(word.substring(0, 6));
            }
        }
    }

    private Optional<String> instrumentName(String ticker) {
        Set<String> cached = keywordCache.computeIfAbsent(ticker, key -> {
            for (MarketDataProvider provider : providers) {
                if (!provider.isEnabled()) {
                    continue;
                }
                try {
                    Optional<InstrumentInfo> instrument = resilience.execute(
                            provider.sourceName(), () -> provider.findInstrument(key));
                    if (instrument.isPresent() && instrument.get().name() != null) {
                        return Set.of(instrument.get().name());
                    }
                } catch (ExternalSourceUnavailableException exception) {
                    log.debug("Источник '{}' недоступен для справки {}: {}",
                            provider.sourceName(), key, exception.getMessage());
                }
            }
            return Set.of();
        });
        return cached.stream().findFirst();
    }

    private List<String> matchedTickers(NewsItem item, Map<String, Set<String>> keywords) {
        String text = ((item.title() == null ? "" : item.title()) + " "
                + (item.summary() == null ? "" : item.summary())).toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : keywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (keyword.length() < 3) {
                    continue;
                }
                if (text.contains(keyword)) {
                    matched.add(entry.getKey());
                    break;
                }
            }
        }
        return matched;
    }

    /** Загрузка ленты с коротким кэшем: повторные запросы не ходят в сеть. */
    private List<NewsItem> fetchFeedCached(String feedUrl) {
        CachedFeed cached = feedCache.get(feedUrl);
        long ttlSeconds = properties.news().cacheTtlSeconds();
        if (cached != null && cached.fetchedAt().plusSeconds(ttlSeconds).isAfter(Instant.now())) {
            return cached.items();
        }
        List<NewsItem> items = fetchFeed(feedUrl);
        feedCache.put(feedUrl, new CachedFeed(Instant.now(), items));
        return items;
    }

    private List<NewsItem> fetchFeed(String feedUrl) {
        // байты + явная кодировка: заголовок Content-Type без charset декодировался
        // бы как ISO-8859-1 и ломал кириллицу
        byte[] bytes = newsRestClient.get()
                .uri(feedUrl)
                .retrieve()
                .body(byte[].class);
        String body = new String(bytes, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(body, "", Parser.xmlParser());
        List<NewsItem> items = new ArrayList<>();
        for (Element item : document.select("item")) {
            String title = text(item, "title");
            String link = text(item, "link");
            String description = text(item, "description");
            String pubDate = text(item, "pubDate");
            items.add(new NewsItem(
                    title,
                    link,
                    description == null ? null : Jsoup.parse(description).text(),
                    parseDate(pubDate),
                    feedUrl));
        }
        return items;
    }

    private String text(Element item, String tag) {
        Element element = item.selectFirst(tag);
        return element == null || element.text().isBlank() ? null : element.text().trim();
    }

    private Instant parseDate(String pubDate) {
        if (pubDate == null) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(pubDate.trim(), RFC_1123).toInstant();
        } catch (RuntimeException exception) {
            try {
                return Instant.parse(pubDate.trim());
            } catch (RuntimeException ignored) {
                log.debug("Не удалось разобрать дату публикации '{}'", pubDate);
                return null;
            }
        }
    }

    private NewsItemDto toDto(NewsItem item, List<String> matchedTickers) {
        return new NewsItemDto(
                matchedTickers,
                item.title(),
                item.url(),
                item.summary(),
                item.publishedAt(),
                item.feed());
    }

    private record CachedFeed(Instant fetchedAt, List<NewsItem> items) {
    }
}
