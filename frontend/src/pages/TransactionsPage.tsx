import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { request } from '../api/client'
import { formatDate, formatMoney, formatQuantity, transactionTypeLabels } from '../api/format'
import type { Transaction, TransactionType } from '../api/types'
import EmptyState from '../components/EmptyState'
import ErrorBanner from '../components/ErrorBanner'
import Modal from '../components/Modal'
import TransactionForm from '../components/TransactionForm'
import { usePortfolios } from '../portfolios/PortfoliosContext'
import { paginateTransactions, selectTransactions } from './transactions-view'
import './transactions-page.css'

const FILTERS: Array<{ value: TransactionType | ''; label: string }> = [
  { value: '', label: 'Все операции' },
  { value: 'BUY', label: 'Покупки' },
  { value: 'SELL', label: 'Продажи' },
  { value: 'DIVIDEND', label: 'Дивиденды' },
]

/** Управление операциями: список, добавление, редактирование, удаление. */
export default function TransactionsPage() {
  const { selected, loading: listLoading, error: listError, refresh } = usePortfolios()

  if (listLoading) return <div className="page-loading">Загрузка…</div>
  if (listError) return <ErrorBanner message={listError} onRetry={() => void refresh()} />
  if (!selected) {
    return (
      <section className="page">
        <h1 className="page-title">Сделки</h1>
        <EmptyState title="Нет портфеля" hint="Сначала создайте портфель на дашборде."
          action={<Link className="btn btn-primary" to="/">К дашборду</Link>} />
      </section>
    )
  }
  return <PortfolioTransactions key={selected.id} portfolioId={selected.id} name={selected.name} />
}

function PortfolioTransactions({ portfolioId, name }: { portfolioId: number; name: string }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const tickerParams = searchParams.getAll('ticker')
  const exactTicker = tickerParams.length === 1 ? tickerParams[0].trim().toLowerCase() : ''
  const searchKey = searchParams.toString()
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<TransactionType | ''>('')
  const [modal, setModal] = useState<{ mode: 'create' } | { mode: 'edit'; transaction: Transaction } | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Transaction | null>(null)

  const [ticker, setTicker] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [order, setOrder] = useState<'asc' | 'desc'>('desc')
  const [requestedPage, setPage] = useState(1)
  const [previousSearch, setPreviousSearch] = useState(searchKey)
  if (searchKey !== previousSearch) {
    setPreviousSearch(searchKey)
    setPage(1)
  }
  const generation = useRef(0)
  const active = useRef(false)
  const invalidRange = Boolean(from && to && from > to)
  const matches = useMemo(() => selectTransactions(transactions, { ticker, exactTicker, type: filter, from, to, order }),
    [transactions, ticker, exactTicker, filter, from, to, order])
  const effectivePage = searchKey !== previousSearch ? 1 : requestedPage
  const { page, pageCount, rows } = paginateTransactions(matches, effectivePage)
  // Keep the last available page as the new position after a list refresh shrinks the results.
  if (page !== effectivePage) setPage(page)
  function changeCriteria<T>(setValue: (value: T) => void, value: T) {
    setValue(value)
    setPage(1)
  }
  const clearExact = () => {
    if (searchParams.has('ticker')) {
      const next = new URLSearchParams(searchParams)
      next.delete('ticker')
      setSearchParams(next)
    }
  }
  const reset = () => { clearExact(); setTicker(''); setFilter(''); setFrom(''); setTo(''); setOrder('desc'); setPage(1) }

  const load = useCallback(async () => {
    const current = ++generation.current
    setLoading(true)
    setError(null)
    try {
      const list = await request<Transaction[]>(`/portfolios/${portfolioId}/transactions`)
      if (active.current && current === generation.current) setTransactions(list)
    } catch (e) {
      if (active.current && current === generation.current) setError(e instanceof Error ? e.message : 'Не удалось загрузить операции')
    } finally {
      if (active.current && current === generation.current) setLoading(false)
    }
  }, [portfolioId])

  useEffect(() => {
    active.current = true
    void load()
    return () => { active.current = false; generation.current++ }
  }, [load])

  async function remove(transaction: Transaction) {
    try {
      await request<void>(`/portfolios/${portfolioId}/transactions/${transaction.id}`, {
        method: 'DELETE',
      })
      if (active.current) await load()
    } catch (e) {
      if (active.current) setError(e instanceof Error ? e.message : 'Не удалось удалить операцию')
    }
  }

  return (
    <section className="page">
      <div className="page-header">
        <h1 className="page-title">Сделки — {name}</h1>
        <div className="page-actions">
          <button type="button" className="btn btn-primary" onClick={() => setModal({ mode: 'create' })}>
            Добавить операцию
          </button>
        </div>
      </div>
      <div className="transactions-filters">
        <label className="field"><span className="field-label">Поиск по тикеру</span><input value={ticker} onChange={event => { clearExact(); changeCriteria(setTicker, event.target.value) }} /></label>
        {exactTicker && <span>Точный тикер: {exactTicker.toUpperCase()}</span>}
        <label className="field"><span className="field-label">Вид операции</span><select value={filter} onChange={(event) => changeCriteria(setFilter, event.target.value as TransactionType | '')}>
          {FILTERS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select></label>
        <label className="field"><span className="field-label">С даты</span><input type="date" value={from} onChange={event => changeCriteria(setFrom, event.target.value)} /></label>
        <label className="field"><span className="field-label">По дату</span><input type="date" value={to} onChange={event => changeCriteria(setTo, event.target.value)} /></label>
        <label className="field"><span className="field-label">Сортировка</span><select value={order} onChange={event => changeCriteria(setOrder, event.target.value as 'asc' | 'desc')}><option value="desc">Новые сначала</option><option value="asc">Старые сначала</option></select></label>
        <button type="button" className="btn" onClick={reset}>Сбросить фильтры</button>
      </div>
      {invalidRange && <div role="alert" className="error-banner">Дата начала позже даты окончания</div>}

      {error && <ErrorBanner message={error} onRetry={() => void load()} />}
      {loading && <div className="page-loading">Загрузка операций…</div>}

      {!loading && !error && !invalidRange && (
        <div className="transactions-summary">
          <span role="status">Найдено в загруженном списке: {matches.length}</span>
          {pageCount > 1 && (
            <nav className="transactions-pagination" aria-label="Страницы операций">
              <button type="button" className="btn" disabled={page === 1} onClick={() => setPage(page - 1)}>Назад</button>
              <span aria-live="polite">Страница {page} из {pageCount}</span>
              <button type="button" className="btn" disabled={page === pageCount} onClick={() => setPage(page + 1)}>Далее</button>
            </nav>
          )}
        </div>
      )}

      {!loading && !error && !invalidRange && transactions.length === 0 && (
        <div className="card">
          <EmptyState title="Операций пока нет" hint="Добавьте покупку, продажу или дивиденд." />
        </div>
      )}
      {!loading && !error && !invalidRange && transactions.length > 0 && matches.length === 0 && (
        <div className="card"><EmptyState title="По фильтрам ничего не найдено" hint="Измените или сбросьте фильтры." action={<button type="button" className="btn" onClick={reset}>Сбросить фильтры</button>} /></div>
      )}

      {!loading && !error && !invalidRange && matches.length > 0 && (
        <div className="card">
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Дата</th>
                  <th>Вид</th>
                  <th>Бумага</th>
                  <th className="num">Количество</th>
                  <th className="num">Цена</th>
                  <th className="num">Комиссия</th>
                  <th className="num">Сумма</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((transaction) => (
                  <tr key={transaction.id}>
                    <td>{formatDate(transaction.executedAt)}</td>
                    <td>
                      <span className={`badge badge-${transaction.type.toLowerCase()}`}>
                        {transactionTypeLabels[transaction.type]}
                      </span>
                    </td>
                    <td>
                      <Link to={`/security/${encodeURIComponent(transaction.ticker)}`} className="ticker-link">
                        {transaction.ticker}
                      </Link>
                    </td>
                    <td className="num">{formatQuantity(transaction.quantity)}</td>
                    <td className="num">
                      {formatMoney(transaction.price)} {transaction.currency}
                    </td>
                    <td className="num">{formatMoney(transaction.commission)}</td>
                    <td className="num">
                      {formatMoney(transaction.quantity * transaction.price)} {transaction.currency}
                    </td>
                    <td className="row-actions">
                      <button
                        type="button"
                        className="btn btn-small"
                        onClick={() => setModal({ mode: 'edit', transaction })}
                      >
                        Изменить
                      </button>
                      <button
                        type="button"
                        className="btn btn-small btn-danger"
                        onClick={() => setConfirmDelete(transaction)}
                      >
                        Удалить
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {confirmDelete && (
        <Modal title="Удаление операции" onClose={() => setConfirmDelete(null)}>
          <p>
            Удалить операцию {transactionTypeLabels[confirmDelete.type]} {confirmDelete.ticker} от{' '}
            {formatDate(confirmDelete.executedAt)}? Действие нельзя отменить.
          </p>
          <div className="form-actions">
            <button
              type="button"
              className="btn btn-danger"
              onClick={() => {
                const target = confirmDelete
                setConfirmDelete(null)
                void remove(target)
              }}
            >
              Удалить
            </button>
            <button type="button" className="btn" onClick={() => setConfirmDelete(null)}>
              Отмена
            </button>
          </div>
        </Modal>
      )}

      {modal && (
        <Modal
          title={modal.mode === 'create' ? 'Новая операция' : 'Изменение операции'}
          onClose={() => setModal(null)}
        >
          <TransactionForm
            portfolioId={portfolioId}
            transaction={modal.mode === 'edit' ? modal.transaction : undefined}
            onSaved={() => {
              if (!active.current) return
              setModal(null)
              void load()
            }}
            onCancel={() => setModal(null)}
          />
        </Modal>
      )}
    </section>
  )
}
