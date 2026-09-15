import { OnboardingWalkthrough } from '../../components/OnboardingWalkthrough'
import type { OnboardingScene } from '../../components/OnboardingWalkthrough'
import styles from './PassengerOnboarding.module.css'

export interface PassengerOnboardingProps {
  /**
   * The real inviting driver's display name, when the page rendering this
   * walkthrough already has it (`PassengerLanding.tsx`'s own `invitation`,
   * loaded before onboarding can ever auto-show or be opened manually --
   * see that page's own KDoc for why `invitation` is never null at either
   * of those call sites). Optional and defaults to a neutral fallback
   * rather than any specific person's name, in case a future caller ever
   * renders this before that data is available.
   */
  driverName?: string
  onComplete: () => void
  onSkip: () => void
}

const FALLBACK_DRIVER_NAME = 'ваш водитель'

const STATUS_SEQUENCE = ['Заявка отправлена', 'Водитель принял', 'Водитель прибыл', 'Поездка началась']

/**
 * PIOS Onboarding v1 (Product Owner exception — see `DriverOnboarding.tsx`'s
 * own doc comment for the same note; applies identically here).
 *
 * Static demo content otherwise (no `api/apiClient` import, no real order
 * is created) -- the one exception is the opening scene's inviting-driver
 * name, which now uses the real name passed in from `PassengerLanding.tsx`
 * (previously a hardcoded "Артур", which could show a different real
 * person's name than whoever actually sent this passenger their invite --
 * see [driverName]'s own KDoc). The status sequence is a single static
 * ordered list, not a live-ticking animation: an interval here would keep
 * running from the moment this component mounts, independent of which
 * scene the walkthrough shell is currently showing, and could finish
 * before the status scene is even reached — a static list avoids that
 * mismatch entirely.
 */
export function PassengerOnboarding({ driverName, onComplete, onSkip }: PassengerOnboardingProps) {
  const scenes: OnboardingScene[] = [
    {
      id: 'invitation',
      title: `Вас пригласил ${driverName?.trim() || FALLBACK_DRIVER_NAME}`,
      durationMs: 5000,
    },
    {
      id: 'route',
      title: 'Укажите маршрут',
      durationMs: 5000,
      content: (
        <div className={styles.demoField}>
          <span className={styles.demoFieldLabel}>Откуда → Куда</span>
        </div>
      ),
    },
    {
      id: 'request',
      title: 'Отправьте заявку',
      durationMs: 5000,
      content: <div className={styles.demoButton}>Отправить заявку</div>,
    },
    {
      id: 'status',
      title: 'Статус вашего заказа',
      durationMs: 6000,
      content: (
        <ol className={styles.statusList}>
          {STATUS_SEQUENCE.map((label) => (
            <li key={label}>{label}</li>
          ))}
        </ol>
      ),
    },
    {
      id: 'conclusion',
      title: 'Вы видите, что происходит с вашим заказом',
      durationMs: null,
    },
  ]

  return <OnboardingWalkthrough scenes={scenes} ctaLabel="Продолжить" onComplete={onComplete} onSkip={onSkip} />
}
