CREATE TABLE portfolio (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    base_currency CHAR(3)     NOT NULL DEFAULT 'RUB',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT portfolio_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT portfolio_base_currency_format CHECK (base_currency ~ '^[A-Z]{3}$')
);

CREATE TABLE position (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    portfolio_id    BIGINT        NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    ticker          VARCHAR(32)   NOT NULL,
    instrument_name VARCHAR(200),
    quantity        NUMERIC(20, 8) NOT NULL,
    average_price   NUMERIC(20, 8) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'RUB',
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT position_ticker_format CHECK (ticker ~ '^[A-Z0-9][A-Z0-9._-]{0,31}$'),
    CONSTRAINT position_quantity_positive CHECK (quantity > 0),
    CONSTRAINT position_average_price_non_negative CHECK (average_price >= 0),
    CONSTRAINT position_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT position_unique_ticker_per_portfolio UNIQUE (portfolio_id, ticker)
);

CREATE INDEX position_portfolio_id_idx ON position (portfolio_id);
