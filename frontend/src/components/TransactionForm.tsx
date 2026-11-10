import { useState, type FormEvent } from 'react'
import { ApiError, request } from '../api/client'
import { fromLocalDateTimeValue, toLocalDateTimeValue, transactionTypeLabels } from '../api/format'
import type { Transaction, TransactionRequest, TransactionType } from '../api/types'
import TickerSearch from './TickerSearch'

const TYPES: TransactionType[] = ['BUY', 'SELL', 'DIVIDEND']

interface FormState {
  type: TransactionType
  ticker: string
  quantity: string
  price: string
  commission: string
  currency: string
  executedAt: string
  instrumentName: string
}

function initialState(transaction?: Transaction): FormState {
  return {
    type: transaction?.type ?? 'BUY',
    ticker: transaction?.ticker ?? '',
    quantity: transaction ? String(transaction.quantity) : '',
    price: transaction ? String(transaction.price) : '',
    commission: transaction && transaction.commission !== 0 ? String(transaction.commission) : '',
    currency: transaction?.currency ?? '',
    executedAt: toLocalDateTimeValue(transaction?.executedAt),
    instrumentName: '',
  }
}

/**
 * Форма создания/редактирования операции. PUT полностью заменяет операцию,
 * поэтому при редактировании все поля отправляются, включая дату и валюту.
 */
export default function TransactionForm({
  portfolioId,
  transaction,
  onSaved,
  onCancel,
}: {
  portfolioId: number
  transaction?: Transaction
  onSaved: () => void
  onCancel: () => void
}) {
  const [form, setForm] = useState<FormState>(() => initialState(transaction))
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const isEdit = transaction !== undefined

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$/.test(form.ticker.trim())) {
      setError(new ApiError(400, 'Тикер: буквы и цифры, до 32 символов (допустимы точка, дефис, подчёркивание)', {}))
      return
    }
    if (!(Number(form.quantity) > 0)) {
      setError(new ApiError(400, 'Количество должно быть больше нуля', {}))
      return
    }
    if (!(Number(form.price) >= 0)) {
      setError(new ApiError(400, 'Цена не может быть отрицательной', {}))
      return
    }

    const body: TransactionRequest = {
      type: form.type,
      ticker: form.ticker.trim().toUpperCase(),
      quantity: form.quantity,
      price: form.price,
    }
    if (form.commission) body.commission = form.commission
    if (form.currency) body.currency = form.currency.toUpperCase()
    if (form.executedAt) body.executedAt = fromLocalDateTimeValue(form.executedAt)
    if (form.instrumentName.trim()) body.instrumentName = form.instrumentName.trim()

    setBusy(true)
    try {
      const path = `/portfolios/${portfolioId}/transactions`
      if (isEdit) {
        await request<Transaction>(`${path}/${transaction.id}`, { method: 'PUT', body })
      } else {
        await request<Transaction>(path, { body })
      }
      onSaved()
    } catch (e) {
      setError(
        e instanceof ApiError ? e : new ApiError(0, 'Не удалось сохранить операцию', {}),
      )
    } finally {
      setBusy(false)
    }
  }

  const fieldErrors = error?.fieldErrors ?? {}

  return (
    <form className="transaction-form" onSubmit={submit}>
      <div className="form-row">
        <label className="field">
          <span className="field-label">Вид операции</span>
          <select
            value={form.type}
            onChange={(event) => update('type', event.target.value as TransactionType)}
          >
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {transactionTypeLabels[type]}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span className="field-label">Тикер</span>
          <TickerSearch
            value={form.ticker}
            onChange={(ticker) => update('ticker', ticker)}
            onPick={(security) => update('instrumentName', security.name)}
          />
          {fieldErrors.ticker && <span className="field-error">{fieldErrors.ticker}</span>}
        </label>
      </div>

      <div className="form-row">
        <label className="field">
          <span className="field-label">Количество</span>
          <input
            type="number"
            step="any"
            min="0"
            value={form.quantity}
            onChange={(event) => update('quantity', event.target.value)}
            required
          />
          {fieldErrors.quantity && <span className="field-error">{fieldErrors.quantity}</span>}
        </label>

        <label className="field">
          <span className="field-label">
            {form.type === 'DIVIDEND' ? 'Дивиденд на бумагу' : 'Цена за бумагу'}
          </span>
          <input
            type="number"
            step="any"
            min="0"
            value={form.price}
            onChange={(event) => update('price', event.target.value)}
            required
          />
          {fieldErrors.price && <span className="field-error">{fieldErrors.price}</span>}
        </label>
      </div>

      <div className="form-row">
        <label className="field">
          <span className="field-label">Комиссия</span>
          <input
            type="number"
            step="any"
            min="0"
            value={form.commission}
            onChange={(event) => update('commission', event.target.value)}
            placeholder="0"
          />
          {fieldErrors.commission && (
            <span className="field-error">{fieldErrors.commission}</span>
          )}
        </label>

        <label className="field">
          <span className="field-label">Валюта</span>
          <input
            type="text"
            value={form.currency}
            onChange={(event) => update('currency', event.target.value.toUpperCase())}
            placeholder="RUB"
            maxLength={3}
          />
          {fieldErrors.currency && <span className="field-error">{fieldErrors.currency}</span>}
        </label>
      </div>

      <div className="form-row">
        <label className="field">
          <span className="field-label">Дата и время</span>
          <input
            type="datetime-local"
            value={form.executedAt}
            onChange={(event) => update('executedAt', event.target.value)}
          />
          <span className="field-hint">пусто — сейчас</span>
          {fieldErrors.executedAt && (
            <span className="field-error">{fieldErrors.executedAt}</span>
          )}
        </label>

        <label className="field">
          <span className="field-label">Название бумаги</span>
          <input
            type="text"
            value={form.instrumentName}
            onChange={(event) => update('instrumentName', event.target.value)}
            placeholder="необязательно"
            maxLength={200}
          />
          {fieldErrors.instrumentName && (
            <span className="field-error">{fieldErrors.instrumentName}</span>
          )}
        </label>
      </div>

      {error && Object.keys(fieldErrors).length === 0 && (
        <div className="error-banner" role="alert">
          {error.message}
        </div>
      )}

      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Сохраняем…' : isEdit ? 'Сохранить' : 'Добавить'}
        </button>
        <button type="button" className="btn" onClick={onCancel}>
          Отмена
        </button>
      </div>
    </form>
  )
}
