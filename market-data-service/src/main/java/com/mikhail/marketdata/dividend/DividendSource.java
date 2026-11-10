package com.mikhail.marketdata.dividend;

import com.mikhail.common.marketdata.Dividend;

import java.util.List;

/**
 * Источник дивидендных выплат: T-Invest API, публичные источники (smart-lab).
 * Провайдер бросает исключение при недоступности источника — дальше
 * сервисный слой переключается на следующий.
 */
public interface DividendSource {

    String sourceName();

    List<Dividend> findDividends(String ticker);

    /** Источник включён (например, у T-Invest не задан токен) — такие источники пропускаются. */
    default boolean isEnabled() {
        return true;
    }
}
