/** Сообщение об ошибке с кнопкой повтора. */
export default function ErrorBanner({
  message,
  onRetry,
}: {
  message: string
  onRetry?: () => void
}) {
  return (
    <div className="error-banner" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="btn btn-small" onClick={onRetry}>
          Повторить
        </button>
      )}
    </div>
  )
}
