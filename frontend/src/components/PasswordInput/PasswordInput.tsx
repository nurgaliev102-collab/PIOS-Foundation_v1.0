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
        {visible ? '🙈' : '👁️'}
      </button>
    </div>
  )
}
