-- Настройки пользователя: порог алертов на изменение цены и одноразовый код привязки Telegram-бота.
-- Строки нет — настройки не создаются, пока пользователь их не сохранил.

CREATE TABLE user_settings (
    user_id                        BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    price_alert_threshold          NUMERIC(6, 2),
    telegram_link_code             VARCHAR(12),
    telegram_link_code_expires_at  TIMESTAMPTZ,
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_settings_threshold_positive
        CHECK (price_alert_threshold IS NULL OR (price_alert_threshold > 0 AND price_alert_threshold <= 100))
);
