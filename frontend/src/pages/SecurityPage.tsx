import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { request } from '../api/client'
import { formatDate, formatDateTime, formatMoney } from '../api/format'
import type { Dividends, NewsItem, Quote, QuoteCandle } from '../api/types'
import ErrorBanner from '../components/ErrorBanner'

/** Страница бумаги: котировка, история, дивиденды и новости по тикеру. */
export default function SecurityPage() {
  const { ticker = '' } = useParams()

  const [quote, setQuote] = useState<Quote | null>(null)
  const [quoteError, setQuoteError] = useState<string | null>(null)
  const [candles, setCandles] = useState<QuoteCandle[]>([])
  const [candlesError, setCandlesError] = useState<string | null>(null)
  const [dividends, setDividends] = useState<Dividends | null>(null)
  const [dividendsError, setDividendsError] = useState<string | null>(null)
  const [news, setNews] = useState<NewsItem[]>([])
  const [newsError, setNewsError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setQuote(null)
    setQuoteError(null)
    setCandles([])
    setCandlesError(null)
    setDividends(null)
    setDividendsError(null)
    setNews([])
    setNewsError(null)

    const encoded = encodeURIComponent(ticker)
    Promise.allSettled([
      request<Quote>(`/quotes/${encoded}`, { market: true }),
      request<QuoteCandle[]>(`/quotes/${encoded}/history`, { market: true }),
      request<Dividends>(`/dividends/${encoded}`, { market: true }),
      request<NewsItem[]>(`/news?tickers=${encoded}`, { market: true }),
    ]).then(([quoteResult, candlesResult, dividendsResult, newsResult]) => {
      if (cancelled) return

      if (quoteResult.status === 'fulfilled') {
        setQuote(quoteResult.value)
      } else {
        setQuoteError(messageOf(quoteResult.reason, 'Котировка не найдена'))
      }

      if (candlesResult.status === 'fulfilled') {
        setCandles(candlesResult.value)
      } else {
        setCandlesError(messageOf(candlesResult.reason, 'История котировок недоступна'))
      }

      if (dividendsResult.status === 'fulfilled') {
        setDividends(dividendsResult.value)
      } else {
        setDividendsError(messageOf(dividendsResult.reason, 'Дивиденды не найдены'))
      }

      if (newsResult.status === 'fulfilled') {
        setNews(newsResult.value)
      } else {
        setNewsError(messageOf(newsResult.reason, 'Новости недоступны'))
      }

      setLoading(false)
    })

    return () => {
      cancelled = true
    }
  }, [ticker])

  return (
    <section className="page">
      <div className="page-header">
        <h1 className="page-title">{ticker}</h1>
        <Link to="/" className="btn btn-ghost">
          ← К дашборду
        </Link>
      </div>

      {loading && <div className="page-loading">Загрузка данных по бумаге…</div>}

      {!loading && (
        <>
          <div className="card">
            <h2 className="card-title">Котировка</h2>
            {quoteError && <ErrorBanner message={quoteError} />}
            {quote && (
              <div className="quote-row">
                <div className="quote-price">
                  {formatMoney(quote.price)} {quote.currency}
                </div>
                <div className="muted">
                  на {formatDateTime(quote.quotedAt)} · источник: {quote.source}
                  {quote.cached && <span className="badge badge-cached"> из архива</span>}
                </div>
              </div>
            )}
            {!quote && !quoteError && <p className="muted">Котировка не получена.</p>}
          </div>

          <div className="card">
            <h2 className="card-title">История котировки (30 дней)</h2>
            {candlesError && <ErrorBanner message={candlesError} />}
            {candles.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={candles} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="date" tickFormatter={formatDate} tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} width={70} domain={['auto', 'auto']} />
                  <Tooltip formatter={(value) => formatMoney(Number(value))} />
                  <Line type="monotone" dataKey="close" name="Цена закрытия" stroke="#2563eb" dot={false} strokeWidth={2} />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              !candlesError && <p className="muted">История котировок пока не накоплена.</p>
            )}
          </div>

          <div className="two-columns">
            <div className="card">
              <h2 className="card-title">Дивиденды</h2>
              {dividendsError && <ErrorBanner message={dividendsError} />}
              {dividends && (
                <>
                  <h3 className="card-subtitle">Предстоящие выплаты</h3>
                  {dividends.upcoming.length === 0 ? (
                    <p className="muted">Предстоящих выплат нет.</p>
                  ) : (
                    <div className="table-wrap">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>Дата отсечки</th>
                            <th>Дата выплаты</th>
                            <th className="num">На бумагу</th>
                            <th>Валюта</th>
                          </tr>
                        </thead>
                        <tbody>
                          {dividends.upcoming.map((dividend, index) => (
                            <tr key={`up-${index}`}>
                              <td>{formatDate(dividend.exDate)}</td>
                              <td>{formatDate(dividend.paymentDate)}</td>
                              <td className="num">{formatMoney(dividend.amountPerShare)}</td>
                              <td>{dividend.currency ?? '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}

                  <h3 className="card-subtitle">Прошедшие выплаты</h3>
                  {dividends.past.length === 0 ? (
                    <p className="muted">Прошедших выплат в источнике нет.</p>
                  ) : (
                    <div className="table-wrap">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>Дата отсечки</th>
                            <th>Дата выплаты</th>
                            <th className="num">На бумагу</th>
                            <th>Валюта</th>
                          </tr>
                        </thead>
                        <tbody>
                          {dividends.past.map((dividend, index) => (
                            <tr key={`past-${index}`}>
                              <td>{formatDate(dividend.exDate)}</td>
                              <td>{formatDate(dividend.paymentDate)}</td>
                              <td className="num">{formatMoney(dividend.amountPerShare)}</td>
                              <td>{dividend.currency ?? '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="card">
              <h2 className="card-title">Новости</h2>
              {newsError && <ErrorBanner message={newsError} />}
              {news.length === 0 && !newsError && (
                <p className="muted">По этой бумаге новостей нет.</p>
              )}
              <ul className="news-list">
                {news.map((item, index) => (
                  <li key={index} className="news-item">
                    {item.url ? (
                      <a href={item.url} target="_blank" rel="noreferrer">
                        {item.title}
                      </a>
                    ) : (
                      <span>{item.title}</span>
                    )}
                    <div className="muted small">
                      {formatDateTime(item.publishedAt)}
                      {item.feed && <> · {item.feed}</>}
                    </div>
                    {item.summary && <p className="news-summary">{item.summary}</p>}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>
      )}
    </section>
  )
}

function messageOf(reason: unknown, fallback: string): string {
  return reason instanceof Error ? reason.message : fallback
}
