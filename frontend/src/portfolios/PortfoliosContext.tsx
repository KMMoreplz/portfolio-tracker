import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { request } from '../api/client'
import type { Portfolio } from '../api/types'

const SELECTED_KEY = 'pt.portfolioId'

interface PortfoliosContextValue {
  portfolios: Portfolio[]
  loading: boolean
  error: string | null
  selected: Portfolio | null
  select(id: number): void
  refresh(): Promise<void>
  create(name: string, baseCurrency: string): Promise<Portfolio>
}

const PortfoliosContext = createContext<PortfoliosContextValue | null>(null)

/**
 * Список портфелей и выбранный портфель — общий для дашборда, сделок
 * и дивидендного календаря, выбор переживает переходы между страницами.
 */
export function PortfoliosProvider({ children }: { children: ReactNode }) {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(() => {
    const stored = localStorage.getItem(SELECTED_KEY)
    return stored ? Number(stored) : null
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const applyList = useCallback((list: Portfolio[]) => {
    setPortfolios(list)
    setSelectedId((current) => {
      const valid = current !== null && list.some((portfolio) => portfolio.id === current)
      const next = valid ? current : (list[0]?.id ?? null)
      if (next !== null) {
        localStorage.setItem(SELECTED_KEY, String(next))
      } else {
        localStorage.removeItem(SELECTED_KEY)
      }
      return next
    })
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = await request<Portfolio[]>('/portfolios')
      applyList(list)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось загрузить портфели')
    } finally {
      setLoading(false)
    }
  }, [applyList])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const select = useCallback((id: number) => {
    localStorage.setItem(SELECTED_KEY, String(id))
    setSelectedId(id)
  }, [])

  const create = useCallback(
    async (name: string, baseCurrency: string) => {
      const created = await request<Portfolio>('/portfolios', {
        body: { name, baseCurrency },
      })
      applyList([...portfolios, created])
      select(created.id)
      return created
    },
    [portfolios, applyList, select],
  )

  const selected = portfolios.find((portfolio) => portfolio.id === selectedId) ?? null

  return (
    <PortfoliosContext.Provider
      value={{ portfolios, loading, error, selected, select, refresh, create }}
    >
      {children}
    </PortfoliosContext.Provider>
  )
}

export function usePortfolios(): PortfoliosContextValue {
  const value = useContext(PortfoliosContext)
  if (!value) {
    throw new Error('usePortfolios должен вызываться внутри PortfoliosProvider')
  }
  return value
}
