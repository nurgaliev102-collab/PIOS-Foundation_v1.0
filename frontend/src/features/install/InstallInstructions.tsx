import { ActionButton } from '../../components/ActionButton'
import { shareInstallLink } from './installPrompt'
import styles from './InstallInstructions.module.css'

export interface InstallInstructionsProps {
  /** Already resolved by the caller (auto-detected or overridden via "У меня другое устройство") — this component never detects anything itself. */
  platform: 'ios' | 'android'
  /** Only meaningful when `platform === 'ios'` — Section 4's own "wrong browser" branch. */
  isSafari: boolean
}

interface Step {
  icon: string
  title: string
}

const IOS_STEPS: Step[] = [
  { icon: '🧭', title: 'Откройте PIOS в Safari' },
  { icon: '⬆️', title: 'Нажмите «Поделиться»' },
  { icon: '➕', title: 'Выберите «На экран «Домой»»' },
  { icon: '✅', title: 'Нажмите «Добавить»' },
]

const ANDROID_STEPS: Step[] = [
  { icon: '🌐', title: 'Откройте PIOS в Chrome' },
  { icon: '⋮', title: 'Нажмите ⋮' },
  { icon: '📲', title: 'Выберите «Установить PIOS» или «Установить приложение»' },
  { icon: '✅', title: 'Подтвердите установку' },
]

/**
 * PIOS Install v1 — the visual, numbered step list (Section 12: "короткий
 * текст, номер шага, визуальная иллюстрация, явное действие" per step; no
 * long-form documentation page). Emoji icons, not custom illustrations —
 * the same "иллюстрация без ассетов" convention `RIDE_STATUS_LABEL` and
 * `DriverHome.tsx`'s own availability card already establish (🟢/🔴/📅/✅),
 * so this reads as one more PIOS screen, not a separate help document.
 */
export function InstallInstructions({ platform, isSafari }: InstallInstructionsProps) {
  if (platform === 'ios' && !isSafari) {
    return (
      <div className={styles.wrongBrowser}>
        <p className={styles.wrongBrowserText}>Чтобы установить PIOS на iPhone, откройте эту страницу в Safari.</p>
        <ActionButton
          label="Скопировать ссылку"
          variant="primary"
          onClick={() => void shareInstallLink(window.location.href)}
        />
        <p className={styles.wrongBrowserHint}>Вставьте её в адресную строку Safari.</p>
      </div>
    )
  }

  const steps = platform === 'ios' ? IOS_STEPS : ANDROID_STEPS
  const finalText =
    platform === 'ios'
      ? 'Готово. Значок PIOS появится на экране телефона.'
      : 'Готово. PIOS появится среди приложений на телефоне.'

  return (
    <div className={styles.instructions}>
      <ol className={styles.stepList}>
        {steps.map((step, index) => (
          <li key={step.title} className={styles.step}>
            <span className={styles.stepIcon} aria-hidden="true">
              {step.icon}
            </span>
            <div>
              <span className={styles.stepNumber}>Шаг {index + 1}</span>
              <p className={styles.stepTitle}>{step.title}</p>
            </div>
          </li>
        ))}
      </ol>
      <p className={styles.finalText}>{finalText}</p>
    </div>
  )
}
