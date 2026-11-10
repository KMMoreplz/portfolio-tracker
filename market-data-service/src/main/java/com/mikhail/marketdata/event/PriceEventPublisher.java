package com.mikhail.marketdata.event;

/**
 * Публикация событий об изменении цены (Kafka или REST-вебхук).
 * Реализация не должна бросать исключений: сбой доставки не должен
 * останавливать обновление котировок.
 */
public interface PriceEventPublisher {

    void publish(PriceChangedEvent event);
}
