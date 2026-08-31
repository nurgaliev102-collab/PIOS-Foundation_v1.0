import { ActionButton } from '../../components/ActionButton'
import styles from './InstallSuccess.module.css'

export interface InstallSuccessProps {
  onContinue: () => void
}

/**
 * PIOS Install v1 — shown only after the browser's own `appinstalled`
 * event actually fires (`installPrompt.ts::onInstalled`), never after a
 * click alone. This is the one screen allowed to say "установлен" at all
 * (Section 13: "не утверждать «установлено», если браузер не позволяет
 * достоверно это определить") — the manual iPhone/Android instructions
 * intentionally end with a softer "Готово. Значок появится..." instead,
 * since there is no equivalent browser confirmation for those paths.
 */
export function InstallSuccess({ onContinue }: InstallSuccessProps) {
  return (
    <div className={styles.success}>
      <span className={styles.icon} aria-hidden="true">
        ✅
      </span>
      <h2 className={styles.title}>PIOS установлен</h2>
      <p className={styles.text}>Теперь вы можете открывать PIOS прямо с экрана телефона.</p>
      <ActionButton label="Перейти в PIOS" variant="primary" onClick={onContinue} />
    </div>
  )
}
