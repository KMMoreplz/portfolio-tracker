/** Типы REST API portfolio-service и market-data-service. */

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  userId: number
  email: string
}

export interface AuthUser {
  id: number
  email: string
}

export interface Portfolio {
  id: number
  name: string
  baseCurrency: string
  createdAt: string
}

export interface PositionMetrics {
  ticker: string
  instrumentName: string | null
  quantity: number
  averagePrice: number
  currentPrice: number
  priceSource: string
  priceAsOf: string | null
  currency: string
  value: number
  cost: number
  profit: number
  profitPercent: number | null
  weightPercent: number
  valueInBase: number
}

export interface Metrics {
  portfolioId: number
  portfolioName: string
  baseCurrency: string
  totalValue: number
  totalCost: number
  profit: number
  profitPercent: number | null
  dividendsReceived: number
  positions: PositionMetrics[]
  calculatedAt: string
}

export interface ValueHistoryPoint {
  date: string
  totalValue: number
  invested: number
}

export interface ValueHistory {
  portfolioId: number
  baseCurrency: string
  priceSource: string
  points: ValueHistoryPoint[]
}

export type TransactionType = 'BUY' | 'SELL' | 'DIVIDEND'

export interface Transaction {
  id: number
  portfolioId: number
  type: TransactionType
  ticker: string
  quantity: number
  price: number
  commission: number
  currency: string
  executedAt: string
}

export interface TransactionRequest {
  type: TransactionType
  ticker: string
  quantity: string
  price: string
  commission?: string
  currency?: string
  executedAt?: string
  instrumentName?: string
}

export interface Security {
  id: number
  ticker: string
  name: string
  currency: string
  isin: string | null
  createdAt: string
}

export interface DividendCalendarItem {
  ticker: string
  instrumentName: string | null
  quantity: number
  exDate: string | null
  paymentDate: string
  amountPerShare: number
  currency: string | null
  payout: number
  payoutInBase: number | null
  source: string | null
}

export interface DividendCalendar {
  portfolioId: number
  baseCurrency: string
  marketDataAvailable: boolean
  items: DividendCalendarItem[]
}

export interface Settings {
  priceAlertThreshold: number | null
  telegramLinkCode: string | null
  telegramLinkCodeExpiresAt: string | null
}

export interface Quote {
  ticker: string
  price: number
  currency: string
  quotedAt: string
  source: string
  cached: boolean
}

export interface QuoteCandle {
  date: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface Dividend {
  ticker: string
  declaredDate: string | null
  exDate: string | null
  recordDate: string | null
  paymentDate: string | null
  amountPerShare: number
  currency: string | null
  regularity: string | null
  type: string | null
  status: string
  source: string | null
}

export interface Dividends {
  ticker: string
  source: string | null
  upcoming: Dividend[]
  past: Dividend[]
}

export interface NewsItem {
  tickers: string[]
  title: string
  url: string | null
  summary: string | null
  publishedAt: string
  feed: string | null
}
