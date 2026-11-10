import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { request } from '../api/client'
import { formatDate, formatMoney, formatQuantity, transactionTypeLabels } from '../api/format'
import type { Transaction, TransactionType } from '../api/types'
import EmptyState from '../components/EmptyState'
import ErrorBanner from '../components/ErrorBanner'
import Modal from '../components/Modal'
import TransactionForm from '../components/TransactionForm'
import { usePortfolios } from '../portfolios/PortfoliosContext'

const FILTERS: Array<{ value: TransactionType | ''; label: string }> = [
  { value: '', label: 'Все операции' },
  { value: 'BUY', label: 'Покупки' },
  { value: 'SELL', label: 'Продажи' },
  { value: 'DIVIDEND', label: 'Дивиденды' },
]

/** Управление операциями: список, добавление, редактирование, удаление. */
export default function TransactionsPage() {
  const { selected, loading: listLoading, error: listError, refresh } = usePortfolios()

  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<TransactionType | ''>('')
  const [modal, setModal] = useState<{ mode: 'create' } | { mode: 'edit'; transaction: Transaction } | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Transaction | null>(null)

  const portfolioId = selected?.id ?? null

  const load = useCallback(async () => {
    if (portfolioId === null) return
    setLoading(true)
    setError(null)
    try {
      const query = filter ? `?type=${filter}` : ''
      const list = await request<Transaction[]>(`/portfolios/${portfolioId}/transactions${query}`)
      setTransactions(list)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось загрузить операции')
    } finally {
      setLoading(false)
    }
  }, [portfolioId, filter])

  useEffect(() => {
    void load()
  }, [load])

  async function remove(transaction: Transaction) {
    try {
      await request<void>(`/portfolios/${portfolioId}/transactions/${transaction.id}`, {
        method: 'DELETE',
      })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось удалить операцию')
    }
  }

  if (listLoading) {
    return <div className="page-loading">Загрузка…</div>
  }
  if (listError) {
    return <ErrorBanner message={listError} onRetry={() => void refresh()} />
  }
  if (!selected) {
    return (
      <section className="page">
        <h1 className="page-title">Сделки</h1>
        <EmptyState
          title="Нет портфеля"
          hint="Сначала создайте портфель на дашборде."
          action={
            <Link className="btn btn-primary" to="/">
              К дашборду
            </Link>
          }
        />
      </section>
    )
  }

  return (
    <section className="page">
      <div className="page-header">
        <h1 className="page-title">Сделки — {selected.name}</h1>
        <div className="page-actions">
          <select value={filter} onChange={(event) => setFilter(event.target.value as TransactionType | '')}>
            {FILTERS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <button type="button" className="btn btn-primary" onClick={() => setModal({ mode: 'create' })}>
            Добавить операцию
          </button>
        </div>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => void load()} />}
      {loading && <div className="page-loading">Загрузка операций…</div>}

      {!loading && transactions.length === 0 && !error && (
        <div className="card">
          <EmptyState
            title={filter ? 'Операций такого вида нет' : 'Операций пока нет'}
            hint="Добавьте покупку, продажу или дивиденд — справочник бумаг подскажет тикер."
            action={
              <button type="button" className="btn btn-primary" onClick={() => setModal({ mode: 'create' })}>
                Добавить операцию
              </button>
            }
          />
        </div>
      )}

      {!loading && transactions.length > 0 && (
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
                {transactions.map((transaction) => (
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
            portfolioId={selected.id}
            transaction={modal.mode === 'edit' ? modal.transaction : undefined}
            onSaved={() => {
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
