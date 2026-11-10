package com.mikhail.portfolio.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Изменение настроек. Поле необязательное: {@code null} сбрасывает порог,
 * значение должно лежать в диапазоне (0; 100] процентов.
 */
public record UpdateSettingsRequestDto(
        @DecimalMin(value = "0.01", message = "порог должен быть больше нуля")
        @DecimalMax(value = "100", message = "порог не может быть больше 100 процентов")
        BigDecimal priceAlertThreshold
) {
}
