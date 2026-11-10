/** Форматирование значений для отображения (только представление, без расчётов). */

const money = new Intl.NumberFormat('ru-RU', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const quantityFormat = new Intl.NumberFormat('ru-RU', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 4,
})

export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return money.format(value)
}

export function formatQuantity(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return quantityFormat.format(value)
}

export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${money.format(value)}%`
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString('ru-RU')
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Число для <input type="datetime-local"> из ISO-строки API. */
export function toLocalDateTimeValue(iso: string | null | undefined): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** Значение datetime-local → ISO с часовым поясом для OffsetDateTime API. */
export function fromLocalDateTimeValue(value: string): string | undefined {
  if (!value) return undefined
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return undefined
  return date.toISOString()
}

export const transactionTypeLabels: Record<string, string> = {
  BUY: 'Покупка',
  SELL: 'Продажа',
  DIVIDEND: 'Дивиденд',
}
