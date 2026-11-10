package com.mikhail.common.domain;

/**
 * Валюты, поддерживаемые приложением. Коды — по стандарту ISO 4217.
 */
public enum Currency {

    RUB("Российский рубль"),
    USD("Доллар США"),
    EUR("Евро"),
    CNY("Юань"),
    GBP("Фунт стерлингов"),
    CHF("Швейцарский франк"),
    JPY("Японская иена"),
    KZT("Казахстанский тенге"),
    TRY("Турецкая лира"),
    HKD("Гонконгский доллар"),
    BYN("Белорусский рубль"),
    AMD("Армянский драм"),
    KGS("Киргизский сом"),
    UZS("Узбекский сум");

    private final String displayName;

    Currency(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Трёхбуквенный код валюты, например {@code RUB}. */
    public String getCode() {
        return name();
    }

    /** Возвращает валюту по коду; {@code null}, если код неизвестен. */
    public static Currency fromCode(String code) {
        if (code == null) {
            return null;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
