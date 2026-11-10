import { useEffect, useRef, useState } from 'react'
import { request } from '../api/client'
import type { Security } from '../api/types'

/**
 * Поле тикера с подсказками из справочника бумаг (/api/securities?query=).
 * Ввод произвольный: незнакомый тикер создастся в справочнике при сохранении операции.
 */
export default function TickerSearch({
  value,
  onChange,
  onPick,
}: {
  value: string
  onChange: (ticker: string) => void
  onPick?: (security: Security) => void
}) {
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState<Security[]>([])
  const [open, setOpen] = useState(false)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => {
    window.clearTimeout(timer.current)
    const trimmed = query.trim()
    if (trimmed.length < 1) {
      setSuggestions([])
      return
    }
    timer.current = window.setTimeout(() => {
      request<Security[]>(`/securities?query=${encodeURIComponent(trimmed)}`)
        .then((list) => setSuggestions(list))
        .catch(() => setSuggestions([]))
    }, 250)
    return () => window.clearTimeout(timer.current)
  }, [query])

  function pick(security: Security) {
    onChange(security.ticker)
    onPick?.(security)
    setOpen(false)
    setQuery('')
  }

  return (
    <div className="ticker-search">
      <input
        type="text"
        value={open ? query : value}
        onChange={(event) => {
          const next = event.target.value
          setQuery(next)
          onChange(next.toUpperCase())
          setOpen(true)
        }}
        onFocus={() => {
          setQuery('')
          setOpen(true)
        }}
        onBlur={() => window.setTimeout(() => setOpen(false), 150)}
        placeholder="SBER"
        autoComplete="off"
        spellCheck={false}
        required
      />
      {open && suggestions.length > 0 && (
        <ul className="ticker-suggestions">
          {suggestions.map((security) => (
            <li key={security.id}>
              <button
                type="button"
                onMouseDown={(event) => {
                  event.preventDefault()
                  pick(security)
                }}
              >
                <span className="suggestion-ticker">{security.ticker}</span>
                <span className="suggestion-name">{security.name}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
