package com.mikhail.common.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Портфель — набор позиций, принадлежащий пользователю.
 *
 * @param id           идентификатор портфеля
 * @param name         название портфеля
 * @param baseCurrency базовая валюта, в которой считаются итоги портфеля
 * @param createdAt    время создания
 */
public record Portfolio(
        Long id,
        String name,
        Currency baseCurrency,
        OffsetDateTime createdAt
) {
    public Portfolio {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseCurrency, "baseCurrency");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
