import { useState, type KeyboardEvent } from 'react'
import { ActionButton } from '../../components/ActionButton'
import { verifyOwnerCredential } from './healthPoll'
import { storeOwnerCredential } from './ownerCredential'
import styles from './OwnerControlCenter.module.css'

export interface LoginScreenProps {
  onLoggedIn: () => void
  /** Section 4.3: a saved login is pre-filled after the tab idles out or is reopened — never the password. */
  initialUsername?: string
}

type SubmitStatus = 'idle' | 'checking' | 'wrong-credential' | 'cannot-verify'

/**
 * The login screen (`PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md` Section 5.1).
 * No "forgot password" (nowhere to send it), no registration (there is
 * exactly one owner, ADR-044 Decision 6), no mention of any service name.
 *
 * The three failure states Section 4.3's table requires are kept
 * textually distinct on purpose: a wrong credential says so plainly; an
 * unreachable console says it cannot check right now, never "wrong
 * password" — conflating the two is exactly what that section forbids.
 *
 * Deliberately not a `<form onSubmit>`: [ActionButton] (shared with every
 * other screen in this app) is always `type="button"`, by design, so it
 * never accidentally submits a surrounding form elsewhere it is used —
 * changing that shared component's behavior for this one screen is out of
 * this task's scope. [handleLogin] is invoked directly from the button's
 * own `onClick`, and also from pressing Enter in either field, so both
 * paths reach the same one place.
 */
export function LoginScreen({ onLoggedIn, initialUsername }: LoginScreenProps) {
  const [username, setUsername] = useState(initialUsername ?? '')
  const [password, setPassword] = useState('')
  const [status, setStatus] = useState<SubmitStatus>('idle')

  async function handleLogin() {
    if (status === 'checking' || username.length === 0 || password.length === 0) {
      return
    }
    setStatus('checking')
    const credential = { username, password }
    const outcome = await verifyOwnerCredential(credential)
    if (outcome === 'valid') {
      storeOwnerCredential(credential)
      onLoggedIn()
      return
    }
    setStatus(outcome === 'invalid' ? 'wrong-credential' : 'cannot-verify')
  }

  function handleEnterKey(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      void handleLogin()
    }
  }

  return (
    <div className={styles.loginScreen}>
      <div className={styles.loginCard}>
        <p className={styles.loginBrand}>PIOS</p>
        <p className={styles.loginSubtitle}>Пульт владельца</p>

        <label className={styles.loginLabel} htmlFor="owner-username">
          Логин
        </label>
        <input
          id="owner-username"
          className={styles.loginInput}
          type="text"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          onKeyDown={handleEnterKey}
          disabled={status === 'checking'}
        />

        <label className={styles.loginLabel} htmlFor="owner-password">
          Пароль
        </label>
        <input
          id="owner-password"
          className={styles.loginInput}
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          onKeyDown={handleEnterKey}
          disabled={status === 'checking'}
        />

        <div className={styles.loginButton}>
          <ActionButton
            label={status === 'checking' ? 'Проверяю…' : 'Войти'}
            variant="primary"
            disabled={status === 'checking' || username.length === 0 || password.length === 0}
            onClick={() => void handleLogin()}
          />
        </div>

        {status === 'wrong-credential' && <p className={styles.loginError}>Неверный логин или пароль</p>}
        {status === 'cannot-verify' && (
          <p className={styles.loginError}>Сейчас не удаётся проверить пароль. Попробуйте через минуту</p>
        )}
      </div>
    </div>
  )
}
