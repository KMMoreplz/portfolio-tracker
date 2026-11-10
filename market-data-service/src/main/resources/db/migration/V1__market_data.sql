CREATE TABLE price_quotes
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticker      VARCHAR(32)    NOT NULL,
    price       NUMERIC(20, 8) NOT NULL,
    currency    CHAR(3)        NOT NULL,
    quoted_at   TIMESTAMPTZ    NOT NULL,
    source      VARCHAR(32)    NOT NULL,
    captured_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT price_quotes_ticker_format CHECK (ticker ~ '^[A-Z0-9][A-Z0-9._-]{0,31}$'),
    CONSTRAINT price_quotes_price_non_negative CHECK (price >= 0),
    CONSTRAINT price_quotes_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX price_quotes_ticker_quoted_at_idx ON price_quotes (ticker, quoted_at DESC);

CREATE TABLE tracked_tickers
(
    ticker   VARCHAR(32) PRIMARY KEY,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tracked_tickers_ticker_format CHECK (ticker ~ '^[A-Z0-9][A-Z0-9._-]{0,31}$')
);
