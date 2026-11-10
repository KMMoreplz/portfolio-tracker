import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { request } from '../api/client'
import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatPercent,
  formatQuantity,
} from '../api/format'
import type { Metrics, ValueHistory } from '../api/types'
import CreatePortfolioForm from '../components/CreatePortfolioForm'
import EmptyState from '../components/EmptyState'
import ErrorBanner from '../components/ErrorBanner'
import { usePortfolios } from '../portfolios/PortfoliosContext'

const HISTORY_DAYS = 30

/** Дашборд: показатели портфеля, график стоимости и таблица позиций. */
export default function DashboardPage() {
  const {
    portfolios,
    loading: listLoading,
    error: listError,
    selected,
    select,
    refresh,
  } = usePortfolios()

  const [metrics, setMetrics] = useState<Metrics | null>(null)
  const [history, setHistory] = useState<ValueHistory | null>(null)
  const [pageLoading, setPageLoading] = useState(false)
  const [pageError, setPageError] = useState<string | null>(null)

  const portfolioId = selected?.id ?? null

  useEffect(() => {
    if (portfolioId === null) {
      setMetrics(null)
      setHistory(null)
      return
    }
    let cancelled = false
    setPageLoading(true)
    setPageError(null)
    Promise.all([
      request<Metrics>(`/portfolios/${portfolioId}/metrics`),
      request<ValueHistory>(`/portfolios/${portfolioId}/value-history?days=${HISTORY_DAYS}`),
    ])
      .then(([nextMetrics, nextHistory]) => {
        if (!cancelled) {
          setMetrics(nextMetrics)
          setHistory(nextHistory)
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setPageError(e instanceof Error ? e.message : 'Не удалось загрузить показатели')
        }
      })
      .finally(() => {
        if (!cancelled) setPageLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [portfolioId])

  if (listLoading) {
    return <div className="page-loading">Загрузка портфелей…</div>
  }

  if (listError) {
    return <ErrorBanner message={listError} onRetry={() => void refresh()} />
  }

  if (portfolios.length === 0) {
    return (
      <section className="page">
        <h1 className="page-title">Дашборд</h1>
        <div className="card">
          <h2 className="card-title">Портфелей пока нет</h2>
          <p className="muted">Создайте первый портфель, чтобы добавлять сделки и смотреть аналитику.</p>
          <CreatePortfolioForm />
        </div>
      </section>
    )
  }

  return (
    <section className="page">
      <div className="page-header">
        <h1 className="page-title">Дашборд</h1>
        {portfolios.length > 1 && (
          <label className="portfolio-select">
            Портфель:
            <select
              value={portfolioId ?? ''}
              onChange={(event) => select(Number(event.target.value))}
            >
              {portfolios.map((portfolio) => (
                <option key={portfolio.id} value={portfolio.id}>
                  {portfolio.name}
                </option>
              ))}
            </select>
          </label>
        )}
        {portfolios.length === 1 && (
          <span className="portfolio-name">{selected?.name}</span>
        )}
      </div>

      {pageLoading && <div className="page-loading">Загрузка показателей…</div>}
      {pageError && <ErrorBanner message={pageError} />}

      {!pageLoading && !pageError && metrics && (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-label">Стоимость портфеля</div>
              <div className="stat-value">
                {formatMoney(metrics.totalValue)} {metrics.baseCurrency}
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Результат (P&amp;L)</div>
              <div className={`stat-value ${metrics.profit >= 0 ? 'positive' : 'negative'}`}>
                {formatMoney(metrics.profit)} {metrics.baseCurrency}
                <span className="stat-sub"> {formatPercent(metrics.profitPercent)}</span>
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Вложено</div>
              <div className="stat-value">
                {formatMoney(metrics.totalCost)} {metrics.baseCurrency}
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Дивиденды получены</div>
              <div className="stat-value">
                {formatMoney(metrics.dividendsReceived)} {metrics.baseCurrency}
              </div>
            </div>
          </div>

          {metrics.positions.length === 0 ? (
            <div className="card">
              <EmptyState
                title="Портфель пуст"
                hint="Добавьте первую сделку — покупку, продажу или дивиденд."
                action={
                  <Link className="btn btn-primary" to="/transactions">
                    Перейти к сделкам
                  </Link>
                }
              />
            </div>
          ) : (
            <>
              <div className="card">
                <div className="card-header">
                  <h2 className="card-title">
                    Стоимость портфеля за {HISTORY_DAYS} дней
                  </h2>
                  {history?.priceSource === 'local-fallback' && (
                    <span className="muted">
                      Источник цен: сохранённые котировки — market-data-service недоступен
                    </span>
                  )}
                </div>
                {history && history.points.length > 0 ? (
                  <ResponsiveContainer width="100%" height={280}>
                    <AreaChart data={history.points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                      <XAxis dataKey="date" tickFormatter={formatDate} tick={{ fontSize: 12 }} />
                      <YAxis tick={{ fontSize: 12 }} width={70} />
                      <Tooltip
                        formatter={(value) => formatMoney(Number(value))}
                        labelFormatter={(label) => formatDate(String(label))}
                      />
                      <Area
                        type="monotone"
                        dataKey="totalValue"
                        name="Стоимость"
                        stroke="#2563eb"
                        fill="#2563eb22"
                        strokeWidth={2}
                      />
                      <Area
                        type="monotone"
                        dataKey="invested"
                        name="Вложено"
                        stroke="#16a34a"
                        fill="#16a34a18"
                        strokeWidth={2}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="muted">Нет данных за период — сделок ещё не было.</p>
                )}
              </div>

              <div className="card">
                <h2 className="card-title">Позиции</h2>
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Бумага</th>
                        <th className="num">Количество</th>
                        <th className="num">Ср. цена</th>
                        <th className="num">Текущая цена</th>
                        <th className="num">Стоимость</th>
                        <th className="num">Доля</th>
                        <th className="num">P&amp;L</th>
                      </tr>
                    </thead>
                    <tbody>
                      {metrics.positions.map((position) => (
                        <tr key={position.ticker}>
                          <td>
                            <Link to={`/security/${encodeURIComponent(position.ticker)}`} className="ticker-link">
                              {position.ticker}
                            </Link>
                            {position.instrumentName && (
                              <div className="muted small">{position.instrumentName}</div>
                            )}
                          </td>
                          <td className="num">{formatQuantity(position.quantity)}</td>
                          <td className="num">
                            {formatMoney(position.averagePrice)} {position.currency}
                          </td>
                          <td className="num">
                            {formatMoney(position.currentPrice)} {position.currency}
                            {position.priceSource === 'average-price' && (
                              <div className="muted small">без котировки</div>
                            )}
                          </td>
                          <td className="num">
                            {formatMoney(position.valueInBase)} {metrics.baseCurrency}
                          </td>
                          <td className="num">{formatPercent(position.weightPercent)}</td>
                          <td className={`num ${position.profit >= 0 ? 'positive' : 'negative'}`}>
                            {formatMoney(position.profit)} {metrics.baseCurrency}
                            <div className="small">{formatPercent(position.profitPercent)}</div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="muted small">
                  Оценка выполнена {formatDateTime(metrics.calculatedAt)}.
                </p>
              </div>
            </>
          )}
        </>
      )}
    </section>
  )
}
