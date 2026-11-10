package com.mikhail.marketdata.web.dto;

import java.util.List;

/** Тело {@code POST /api/tickers}: тикеры, за которыми нужно следить. */
public record TrackTickersRequest(List<String> tickers) {
}
