import { OnboardingWalkthrough } from '../../components/OnboardingWalkthrough'
import type { OnboardingScene } from '../../components/OnboardingWalkthrough'
import styles from './PassengerOnboarding.module.css'

export interface PassengerOnboardingProps {
  onComplete: () => void
  onSkip: () => void
}

const STATUS_SEQUENCE = ['Заявка отправлена', 'Водитель принял', 'Водитель прибыл', 'Поездка началась']

/**
 * PIOS Onboarding v1 (Product Owner exception — see `DriverOnboarding.tsx`'s
 * own doc comment for the same note; applies identically here).
 *
 * Static demo content only ("Артур" as the inviting driver) — no
 * `api/apiClient` import, no read of the real `InvitationInfo` this page
 * actually loaded, no real order is created. The status sequence is a
 * single static ordered list, not a live-ticking animation: an interval
 * here would keep running from the moment this component mounts,
 * independent of which scene the walkthrough shell is currently showing,
 * and could finish before the status scene is even reached — a static list
 * avoids that mismatch entirely.
 */
export function PassengerOnboarding({ onComplete, onSkip }: PassengerOnboardingProps) {
  const scenes: OnboardingScene[] = [
    {
      id: 'invitation',
      title: 'Вас пригласил Артур',
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
