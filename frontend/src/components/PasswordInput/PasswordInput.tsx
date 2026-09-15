import { useId, useState, type KeyboardEvent } from 'react'
import styles from './PasswordInput.module.css'

export interface PasswordInputProps {
  /** Reuses the calling screen's own input styling (border, sizing, focus ring) — this component adds no visual identity of its own beyond the toggle. */
  className: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  ariaLabel?: string
  id?: string
  autoComplete?: string
  disabled?: boolean
  onKeyDown?: (event: KeyboardEvent<HTMLInputElement>) => void
}

/**
 * A password field with a show/hide toggle — hidden by default (`type="password"`),
 * one click reveals the plain text (`type="text"`), a second click hides it
 * again. Shared across every screen with a password field (Driver Home,
 * Passenger Landing, Owner Control Center's login) rather than duplicated
 * three times, since all three need the exact same behavior, unlike the
 * per-screen presentation helpers this codebase otherwise keeps separate.
 *
 * Styling is intentionally left to the caller's own existing input class
 * (`className`) — this component only adds the toggle button and the
 * padding to keep it from overlapping typed text.
 */
export function PasswordInput({
  className,
  value,
  onChange,
  placeholder,
  ariaLabel,
  id,
  autoComplete,
  disabled = false,
  onKeyDown,
}: PasswordInputProps) {
  const [visible, setVisible] = useState(false)
  const generatedId = useId()
  const inputId = id ?? generatedId

  return (
    <div className={styles.wrapper}>
      <input
        id={inputId}
        className={className}
        style={{ paddingRight: '2.75rem' }}
        type={visible ? 'text' : 'password'}
        value={value}
        placeholder={placeholder}
        aria-label={ariaLabel}
        autoComplete={autoComplete}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={onKeyDown}
      />
      <button
        type="button"
        className={styles.toggle}
        aria-label={visible ? 'Скрыть пароль' : 'Показать пароль'}
        aria-controls={inputId}
        aria-pressed={visible}
        onClick={() => setVisible((current) => !current)}
      >
        {visible ? (
          <svg
            className={styles.icon}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M3 3l18 18" />
            <path d="M10.6 5.2A10.4 10.4 0 0 1 12 5c6.5 0 10 7 10 7a15.3 15.3 0 0 1-3.4 4.3M6.6 6.6C4 8.3 2 12 2 12s3.5 7 10 7a9.9 9.9 0 0 0 4.6-1.1" />
            <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
          </svg>
        ) : (
          <svg
            className={styles.icon}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        )}
      </button>
    </div>
  )
}
