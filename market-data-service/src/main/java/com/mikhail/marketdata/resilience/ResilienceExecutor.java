package com.mikhail.marketdata.resilience;

import com.mikhail.marketdata.config.MarketDataProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Обёртка над Resilience4j (core, без Spring-интеграции): ретраи с экспоненциальным
 * backoff внутри circuit breaker'а для каждого внешнего источника отдельно.
 *
 * <p>Любая ошибка источника после исчерпания ретраев превращается в
 * {@link ExternalSourceUnavailableException}, чтобы сервисный слой мог перейти на fallback.
 */
@Component
public class ResilienceExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResilienceExecutor.class);

    private final Retry retry;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public ResilienceExecutor(MarketDataProperties properties) {
        MarketDataProperties.Resilience resilience = properties.resilience();

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(resilience.retry().maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        resilience.retry().waitDurationMs(),
                        resilience.retry().multiplier()))
                .retryOnException(exception -> true)
                .failAfterMaxAttempts(true)
                .build();
        this.retry = Retry.of("market-data-retry", retryConfig);

        MarketDataProperties.Resilience.CircuitBreaker cb = resilience.circuitBreaker();
        this.circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold((float) cb.failureRateThresholdPercent())
                .slidingWindowSize(cb.slidingWindowSize())
                .minimumNumberOfCalls(cb.minimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(cb.waitDurationInOpenStateMs()))
                .permittedNumberOfCallsInHalfOpenState(cb.permittedCallsInHalfOpenState())
                .recordExceptions(Exception.class)
                .build();
    }

    /**
     * Выполняет обращение к источнику: retry → circuit breaker → исходный вызов.
     *
     * @param sourceName имя источника (t-invest, moex-iss, smart-lab, news-rss)
     */
    public <T> T execute(String sourceName, Supplier<T> supplier) {
        CircuitBreaker breaker = breakers.computeIfAbsent(sourceName, this::createBreaker);
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(breaker, supplier);
        Supplier<T> retried = Retry.decorateSupplier(retry, guarded);
        try {
            return retried.get();
        } catch (Exception exception) {
            if (exception instanceof ExternalSourceUnavailableException unavailable) {
                throw unavailable;
            }
            throw new ExternalSourceUnavailableException(sourceName, exception);
        }
    }

    /** Состояние circuit breaker'а источника для диагностики и тестов. */
    public Optional<CircuitBreaker.State> breakerState(String sourceName) {
        return Optional.ofNullable(breakers.get(sourceName)).map(CircuitBreaker::getState);
    }

    /** Сброс состояния источника (используется в тестах). */
    public void reset(String sourceName) {
        Optional.ofNullable(breakers.get(sourceName)).ifPresent(CircuitBreaker::reset);
    }

    private CircuitBreaker createBreaker(String sourceName) {
        CircuitBreaker breaker = CircuitBreaker.of(sourceName, circuitBreakerConfig);
        breaker.getEventPublisher().onStateTransition(event -> log.info(
                "Circuit breaker '{}': {} -> {}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()));
        return breaker;
    }
}
