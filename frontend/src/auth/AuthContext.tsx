import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { request, tokenStore, UNAUTHORIZED_EVENT } from '../api/client'
import type { AuthUser, TokenResponse } from '../api/types'

interface AuthContextValue {
  user: AuthUser | null
  /** true — проверка токена при загрузке ещё идёт, спиннер вместо роутинга. */
  ready: boolean
  login(email: string, password: string): Promise<void>
  register(email: string, password: string): Promise<void>
  logout(): void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [ready, setReady] = useState(false)

  // Восстановление сессии: токен из localStorage проверяем через /api/auth/me.
  useEffect(() => {
    let cancelled = false
    const token = tokenStore.get()
    if (!token) {
      setReady(true)
      return
    }
    request<AuthUser>('/auth/me')
      .then((me) => {
        if (!cancelled) setUser(me)
      })
      .catch(() => {
        tokenStore.clear()
      })
      .finally(() => {
        if (!cancelled) setReady(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 401 от любого запроса: токен уже очищен в client.ts, гасим состояние.
  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const response = await request<TokenResponse>('/auth/login', {
      body: { email, password },
    })
    tokenStore.set(response.accessToken)
    setUser({ id: response.userId, email: response.email })
  }, [])

  const register = useCallback(async (email: string, password: string) => {
    const response = await request<TokenResponse>('/auth/register', {
      body: { email, password },
    })
    tokenStore.set(response.accessToken)
    setUser({ id: response.userId, email: response.email })
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, ready, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth должен вызываться внутри AuthProvider')
  }
  return value
}
