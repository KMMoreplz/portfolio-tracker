type Row = { id: number; ticker: string; type: string; executedAt: string }
type Filters = { ticker: string; exactTicker?: string; type: string; from: string; to: string; order: 'asc' | 'desc' }

function localMidnight(day: string, next = false): number {
  const [year, month, date] = day.split('-').map(Number)
  return new Date(year, month - 1, date + (next ? 1 : 0)).getTime()
}

function instantKey(iso: string) {
  // Date preserves milliseconds; keep the remaining digits for rows within that millisecond.
  const fraction = /\.(\d+)(?=Z$|[+-]\d{2}:\d{2}$)/i.exec(iso)
  if (!fraction) return { milliseconds: Date.parse(iso), remainder: '' }
  const digits = fraction[1]
  const tail = fraction.index + fraction[0].length
  return {
    milliseconds: Date.parse(`${iso.slice(0, fraction.index)}.${digits.slice(0, 3).padEnd(3, '0')}${iso.slice(tail)}`),
    remainder: digits.slice(3).replace(/0+$/, ''),
  }
}

export function selectTransactions<T extends Row>(rows: T[], filters: Filters): T[] {
  const ticker = filters.ticker.trim().toLowerCase()
  const exactTicker = filters.exactTicker?.trim().toLowerCase()
  const start = filters.from ? localMidnight(filters.from) : -Infinity
  const end = filters.to ? localMidnight(filters.to, true) : Infinity
  const direction = filters.order === 'asc' ? 1 : -1
  return rows
    .filter(row => (exactTicker ? row.ticker.trim().toLowerCase() === exactTicker : row.ticker.toLowerCase().includes(ticker)) && (!filters.type || row.type === filters.type))
    .map(row => ({ row, ...instantKey(row.executedAt) }))
    .filter(({ milliseconds }) => milliseconds >= start && milliseconds < end)
    .sort((a, b) => direction * (a.milliseconds - b.milliseconds
      || (a.remainder < b.remainder ? -1 : a.remainder > b.remainder ? 1 : 0)
      || a.row.id - b.row.id))
    .map(({ row }) => row)
}

const PAGE_SIZE = 10

export function paginateTransactions<T>(matches: readonly T[], requestedPage: number) {
  const pageCount = Math.ceil(matches.length / PAGE_SIZE)
  const page = Math.min(Math.max(requestedPage, 1), Math.max(pageCount, 1))
  const offset = (page - 1) * PAGE_SIZE
  return { page, pageCount, rows: matches.slice(offset, offset + PAGE_SIZE) }
}
