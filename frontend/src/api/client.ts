/** Обёртка fetch: JSON, JWT-заголовок, единый разбор ошибок API. */

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

const TOKEN_KEY = 'pt.token'

export const tokenStore = {
  get(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  },
  set(token: string): void {
    localStorage.setItem(TOKEN_KEY, token)
  },
  clear(): void {
    localStorage.removeItem(TOKEN_KEY)
  },
}

/** Событие «токен отвергнут» — AuthContext разлогинивает и уводит на /login. */
export const UNAUTHORIZED_EVENT = 'pt:unauthorized'

export interface RequestOptions {
  method?: string
  body?: unknown
  /** true — ходить в market-data-service (префикс /market), иначе portfolio-service (/api). */
  market?: boolean
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const base = options.market ? '/market' : '/api'
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = tokenStore.get()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const method = options.method ?? (options.body !== undefined ? 'POST' : 'GET')

  let response: Response
  try {
    response = await fetch(base + path, {
      method,
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Сервер недоступен — проверьте, что backend запущен', {})
  }

  if (response.status === 401 && tokenStore.get()) {
    tokenStore.clear()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  let payload: unknown = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = null
    }
  }

  if (!response.ok) {
    const body = payload as { message?: string; fieldErrors?: Array<{ field: string; message: string }> } | null
    const fieldErrors: Record<string, string> = {}
    for (const error of body?.fieldErrors ?? []) {
      fieldErrors[error.field] = error.message
    }
    throw new ApiError(response.status, body?.message ?? `Ошибка запроса (${response.status})`, fieldErrors)
  }

  return payload as T
}
