import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { usePortfolios } from '../portfolios/PortfoliosContext'

/** Создание первого портфеля, когда их ещё нет. */
export default function CreatePortfolioForm({ onCreated }: { onCreated?: () => void }) {
  const { create } = usePortfolios()
  const [name, setName] = useState('')
  const [baseCurrency, setBaseCurrency] = useState('RUB')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await create(name.trim(), baseCurrency)
      setName('')
      onCreated?.()
    } catch (e) {
      setError(
        e instanceof ApiError ? e : new ApiError(0, 'Не удалось создать портфель', {}),
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="create-portfolio-form" onSubmit={submit}>
      <label className="field">
        <span className="field-label">Название портфеля</span>
        <input
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Основной"
          maxLength={100}
          required
        />
        {error?.fieldErrors.name && (
          <span className="field-error">{error.fieldErrors.name}</span>
        )}
      </label>

      <label className="field">
        <span className="field-label">Базовая валюта</span>
        <select value={baseCurrency} onChange={(event) => setBaseCurrency(event.target.value)}>
          <option value="RUB">RUB</option>
          <option value="USD">USD</option>
          <option value="EUR">EUR</option>
        </select>
        {error?.fieldErrors.baseCurrency && (
          <span className="field-error">{error.fieldErrors.baseCurrency}</span>
        )}
      </label>

      {error && !error.fieldErrors.name && !error.fieldErrors.baseCurrency && (
        <div className="error-banner" role="alert">
          {error.message}
        </div>
      )}

      <button type="submit" className="btn btn-primary" disabled={busy}>
        {busy ? 'Создаём…' : 'Создать портфель'}
      </button>
    </form>
  )
}
