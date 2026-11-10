-- Своя маленькая БД telegram-bot-service (H2): только связь чата с пользователем
-- и журнал отправленных напоминаний (чтобы не слать одно и то же повторно).
CREATE TABLE IF NOT EXISTS chat_bindings (
    chat_id        BIGINT       PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    email          VARCHAR(255),
    token          VARCHAR(4096) NOT NULL,
    alerts_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    bound_at       TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS sent_notifications (
    chat_id    BIGINT        NOT NULL,
    dedupe_key VARCHAR(128)  NOT NULL,
    sent_at    TIMESTAMP     NOT NULL,
    PRIMARY KEY (chat_id, dedupe_key)
);
