import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, request } from '../api/client'
import { formatDateTime } from '../api/format'
import type { Settings } from '../api/types'
import ErrorBanner from '../components/ErrorBanner'

/** Настройки: порог алертов на изменение цены и привязка Telegram-бота. */
export default function SettingsPage() {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [threshold, setThreshold] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<ApiError | null>(null)

  const [codeBusy, setCodeBusy] = useState(false)
  const [codeError, setCodeError] = useState<string | null>(null)

  function apply(next: Settings) {
    setSettings(next)
    setThreshold(next.priceAlertThreshold === null ? '' : String(next.priceAlertThreshold))
  }

  useEffect(() => {
    let cancelled = false
    request<Settings>('/settings')
      .then((value) => {
        if (!cancelled) apply(value)
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Не удалось загрузить настройки')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function saveThreshold(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setSaveMessage(null)
    setSaveError(null)
    try {
      const next = await request<Settings>('/settings', {
        method: 'PUT',
        body: { priceAlertThreshold: threshold === '' ? null : Number(threshold) },
      })
      apply(next)
      setSaveMessage('Сохранено')
    } catch (e) {
      setSaveError(
        e instanceof ApiError ? e : new ApiError(0, 'Не удалось сохранить настройки', {}),
      )
    } finally {
      setSaving(false)
    }
  }

  async function generateCode() {
    setCodeBusy(true)
    setCodeError(null)
    try {
      const next = await request<Settings>('/settings/telegram/link-code', { method: 'POST' })
      apply(next)
    } catch (e) {
      setCodeError(e instanceof Error ? e.message : 'Не удалось получить код')
    } finally {
      setCodeBusy(false)
    }
  }

  if (loading) return <div className="page-loading">Загрузка настроек…</div>
  if (error) return <ErrorBanner message={error} />

  return (
    <section className="page">
      <h1 className="page-title">Настройки</h1>

      <div className="card">
        <h2 className="card-title">Привязка Telegram-бота</h2>
        <p className="muted">
          Одноразовый код связывает ваш чат с аккаунтом: введите его боту, и уведомления о
          портфеле начнут приходить в Telegram.
        </p>

        {settings?.telegramLinkCode ? (
          <div className="link-code-box">
            <div className="link-code">{settings.telegramLinkCode}</div>
            <div className="muted small">
              Действует до {formatDateTime(settings.telegramLinkCodeExpiresAt)}
            </div>
          </div>
        ) : (
          <p className="muted">Активного кода нет.</p>
        )}

        {codeError && <ErrorBanner message={codeError} />}

        <button
          type="button"
          className="btn btn-primary"
          onClick={() => void generateCode()}
          disabled={codeBusy}
        >
          {codeBusy ? 'Запрашиваем…' : settings?.telegramLinkCode ? 'Получить новый код' : 'Получить код'}
        </button>
      </div>

      <div className="card">
        <h2 className="card-title">Порог алертов</h2>
        <p className="muted">
          Уведомление приходит, когда цена бумаги изменилась больше чем на указанный процент.
          Пустое поле выключает порог.
        </p>

        <form className="threshold-form" onSubmit={saveThreshold}>
          <label className="field">
            <span className="field-label">Изменение цены, %</span>
            <input
              type="number"
              step="any"
              min="0.01"
              max="100"
              value={threshold}
              onChange={(event) => setThreshold(event.target.value)}
              placeholder="например, 5"
            />
            {saveError?.fieldErrors.priceAlertThreshold && (
              <span className="field-error">{saveError.fieldErrors.priceAlertThreshold}</span>
            )}
          </label>

          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Сохраняем…' : 'Сохранить'}
          </button>

          {saveMessage && <span className="save-ok">{saveMessage}</span>}
        </form>

        {saveError && Object.keys(saveError.fieldErrors).length === 0 && (
          <ErrorBanner message={saveError.message} />
        )}
      </div>
    </section>
  )
}
