-- Домен портфеля: пользователи, справочник бумаг, операции, дивиденды, котировки.

-- Пользователи системы (регистрация и вход).
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Справочник ценных бумаг. Заполняется из операций и внешних источников.
CREATE TABLE securities (
    id         BIGSERIAL PRIMARY KEY,
    ticker     VARCHAR(32)  NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    currency   CHAR(3)      NOT NULL,
    isin       VARCHAR(12),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT securities_ticker_format CHECK (ticker ~ '^[A-Z0-9][A-Z0-9._-]{0,31}$'),
    CONSTRAINT securities_currency_len CHECK (char_length(currency) = 3)
);

-- Операции над портфелем: покупка, продажа, дивиденд.
CREATE TABLE transactions (
    id          BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT       NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    type        VARCHAR(10)   NOT NULL,
    ticker      VARCHAR(32)   NOT NULL,
    quantity    NUMERIC(20,8) NOT NULL,
    price       NUMERIC(20,8) NOT NULL,
    commission  NUMERIC(20,8) NOT NULL DEFAULT 0,
    currency    CHAR(3)       NOT NULL,
    executed_at TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT transactions_type_check CHECK (type IN ('BUY', 'SELL', 'DIVIDEND')),
    CONSTRAINT transactions_quantity_positive CHECK (quantity > 0),
    CONSTRAINT transactions_price_non_negative CHECK (price >= 0),
    CONSTRAINT transactions_commission_non_negative CHECK (commission >= 0)
);

CREATE INDEX idx_transactions_portfolio ON transactions (portfolio_id, executed_at DESC);

-- События дивидендов: отдельная запись на каждую выплату,
-- чтобы строить дивидендный календарь отдельно от журнала операций.
CREATE TABLE dividend_events (
    id               BIGSERIAL PRIMARY KEY,
    portfolio_id     BIGINT       NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    transaction_id   BIGINT       NOT NULL UNIQUE REFERENCES transactions (id) ON DELETE CASCADE,
    ticker           VARCHAR(32)  NOT NULL,
    pay_date         DATE         NOT NULL,
    amount_per_share NUMERIC(20,8) NOT NULL,
    quantity         NUMERIC(20,8) NOT NULL,
    total_amount     NUMERIC(20,8) NOT NULL,
    currency         CHAR(3)      NOT NULL,
    CONSTRAINT dividend_events_amount_positive CHECK (amount_per_share >= 0 AND quantity > 0 AND total_amount >= 0)
);

CREATE INDEX idx_dividend_events_portfolio ON dividend_events (portfolio_id, pay_date DESC);

-- Последние котировки, по которым считалась оценка портфеля
-- (кэш MarketDataProvider; внешние котировки подключаются отдельной задачей).
CREATE TABLE price_quotes (
    ticker     VARCHAR(32)   NOT NULL,
    price      NUMERIC(20,8) NOT NULL,
    currency   CHAR(3)       NOT NULL,
    quoted_at  TIMESTAMPTZ   NOT NULL,
    source     VARCHAR(50)   NOT NULL,
    captured_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (ticker, quoted_at),
    CONSTRAINT price_quotes_price_positive CHECK (price > 0)
);

CREATE INDEX idx_price_quotes_ticker_captured ON price_quotes (ticker, captured_at DESC);
