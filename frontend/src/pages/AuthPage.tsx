import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/** Экран входа и регистрации: один каркас, разные режимы. */
export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const isRegister = mode === 'register'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (isRegister) {
        await register(email.trim(), password)
      } else {
        await login(email.trim(), password)
      }
      navigate('/', { replace: true })
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e
          : new ApiError(0, 'Не удалось выполнить запрос — проверьте, что сервер запущен', {}),
      )
    } finally {
      setBusy(false)
    }
  }

  const fieldErrors = error?.fieldErrors ?? {}

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">Portfolio Tracker</div>
        <div className="auth-tabs">
          <Link to="/login" className={mode === 'login' ? 'auth-tab active' : 'auth-tab'}>
            Вход
          </Link>
          <Link
            to="/register"
            className={mode === 'register' ? 'auth-tab active' : 'auth-tab'}
          >
            Регистрация
          </Link>
        </div>

        {error && (
          <div className="error-banner" role="alert">
            <div>
              <div>{error.message}</div>
              {Object.entries(fieldErrors).map(([field, message]) => (
                <div key={field} className="error-field">
                  {field}: {message}
                </div>
              ))}
            </div>
          </div>
        )}

        <form className="auth-form" onSubmit={submit}>
          <label className="field">
            <span className="field-label">Email</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              autoComplete="email"
              required
            />
            {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
          </label>

          <label className="field">
            <span className="field-label">Пароль</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={isRegister ? 'от 8 символов' : 'Пароль'}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              required
            />
            {fieldErrors.password && <span className="field-error">{fieldErrors.password}</span>}
          </label>

          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Подождите…' : isRegister ? 'Создать аккаунт' : 'Войти'}
          </button>
        </form>

        <p className="auth-hint">
          {isRegister
            ? 'После регистрации сразу выпускается токен — форма входа больше не потребуется.'
            : 'Нет аккаунта? Зарегистрируйтесь — это пара кликов.'}
        </p>
      </div>
    </div>
  )
}
