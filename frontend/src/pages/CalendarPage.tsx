import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { request } from '../api/client'
import { formatDate, formatMoney, formatQuantity } from '../api/format'
import type { DividendCalendar } from '../api/types'
import EmptyState from '../components/EmptyState'
import ErrorBanner from '../components/ErrorBanner'
import { usePortfolios } from '../portfolios/PortfoliosContext'

/** Дивидендный календарь: предстоящие выплаты по бумагам из портфеля. */
export default function CalendarPage() {
  const { portfolios, loading: listLoading, error: listError, selected, select, refresh } =
    usePortfolios()

  const [calendar, setCalendar] = useState<DividendCalendar | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  const portfolioId = selected?.id ?? null

  useEffect(() => {
    if (portfolioId === null) {
      setCalendar(null)
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    request<DividendCalendar>(`/portfolios/${portfolioId}/dividend-calendar`)
      .then((value) => {
        if (!cancelled) setCalendar(value)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Не удалось загрузить календарь')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [portfolioId, reload])

  if (listLoading) return <div className="page-loading">Загрузка…</div>
  if (listError) return <ErrorBanner message={listError} onRetry={() => void refresh()} />

  return (
    <section className="page">
      <div className="page-header">
        <h1 className="page-title">Дивидендный календарь</h1>
        {portfolios.length > 1 && selected && (
          <label className="portfolio-select">
            Портфель:
            <select value={selected.id} onChange={(event) => select(Number(event.target.value))}>
              {portfolios.map((portfolio) => (
                <option key={portfolio.id} value={portfolio.id}>
                  {portfolio.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {!selected && (
        <div className="card">
          <EmptyState
            title="Нет портфеля"
            hint="Создайте портфель на дашборде — календарь покажет выплаты по его бумагам."
            action={
              <Link className="btn btn-primary" to="/">
                К дашборду
              </Link>
            }
          />
        </div>
      )}

      {selected && loading && <div className="page-loading">Загрузка календаря…</div>}
      {selected && error && <ErrorBanner message={error} onRetry={() => setReload((value) => value + 1)} />}
      {selected && !loading && !error && calendar && (
        <>
          {!calendar.marketDataAvailable && (
            <div className="warning-banner" role="alert">
              market-data-service недоступен — данные о предстоящих выплатах временно получить
              нельзя. Список будет пуст не потому, что выплат нет.
            </div>
          )}

          <div className="card">
            <h2 className="card-title">Предстоящие выплаты</h2>
            {calendar.items.length === 0 ? (
              <EmptyState
                title={calendar.marketDataAvailable ? 'Предстоящих выплат нет' : 'Нет данных'}
                hint={
                  calendar.marketDataAvailable
                    ? 'По бумагам текущего портфеля ближайших дивидендов не запланировано.'
                    : 'Источник данных о выплатах недоступен — попробуйте позже.'
                }
              />
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Дата выплаты</th>
                      <th>Дата отсечки</th>
                      <th>Бумага</th>
                      <th className="num">На бумагу</th>
                      <th className="num">Количество</th>
                      <th className="num">Выплата</th>
                      <th className="num">В базовой валюте</th>
                    </tr>
                  </thead>
                  <tbody>
                    {calendar.items.map((item, index) => (
                      <tr key={`${item.ticker}-${index}`}>
                        <td>{formatDate(item.paymentDate)}</td>
                        <td>{formatDate(item.exDate)}</td>
                        <td>
                          <Link to={`/security/${encodeURIComponent(item.ticker)}`} className="ticker-link">
                            {item.ticker}
                          </Link>
                          {item.instrumentName && (
                            <div className="muted small">{item.instrumentName}</div>
                          )}
                        </td>
                        <td className="num">
                          {formatMoney(item.amountPerShare)} {item.currency ?? ''}
                        </td>
                        <td className="num">{formatQuantity(item.quantity)}</td>
                        <td className="num">
                          {formatMoney(item.payout)} {item.currency ?? ''}
                        </td>
                        <td className="num">
                          {item.payoutInBase === null
                            ? '—'
                            : `${formatMoney(item.payoutInBase)} ${calendar.baseCurrency}`}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  )
}
