package com.mikhail.marketdata.resilience;

/**
 * Внешний источник недоступен: исчерпаны ретраи или открыт circuit breaker.
 * Ловится в сервисном слое для перехода на fallback (последние сохранённые котировки).
 */
public class ExternalSourceUnavailableException extends RuntimeException {

    private final String sourceName;

    public ExternalSourceUnavailableException(String sourceName, Throwable cause) {
        super("Внешний источник '" + sourceName + "' недоступен", cause);
        this.sourceName = sourceName;
    }

    public String getSourceName() {
        return sourceName;
    }
}
