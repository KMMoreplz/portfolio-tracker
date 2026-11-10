package com.mikhail.portfolio.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Стоимость портфеля и вложенные средства на конец дня (в базовой валюте).
 *
 * @param totalValue стоимость всех позиций на эту дату
 * @param invested   чистые вложенные деньги: суммы покупок и комиссий минус
 *                   поступления от продаж и дивидендов
 */
public record ValueHistoryPointDto(
        LocalDate date,
        BigDecimal totalValue,
        BigDecimal invested
) {
}
