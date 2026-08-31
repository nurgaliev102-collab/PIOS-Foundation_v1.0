import { DriverCard } from '../../components/DriverCard'
import { QRCard } from '../../components/QRCard'
import { OnboardingWalkthrough } from '../../components/OnboardingWalkthrough'
import type { OnboardingScene } from '../../components/OnboardingWalkthrough'
import styles from './DriverOnboarding.module.css'

export interface DriverOnboardingProps {
  onComplete: () => void
  onSkip: () => void
}

/**
 * PIOS Onboarding v1 (Product Owner exception, see implementation report —
 * this Sprint runs without a prior `E-NNN`/registered hypothesis, as a
 * preparatory step for the first real pilot; its own effectiveness is
 * checked in that pilot and recorded as new `E-NNN` entries afterward, per
 * `PIOS_PRODUCT_EVIDENCE.md`).
 *
 * Every piece of content below is static demo data ("Артур" the driver,
 * "Иван" the passenger, invented order details) — nothing here reads from
 * or writes to any real identity, driver, order, proposal, or assignment.
 * No `api/apiClient` import exists in this file at all, by design: there
 * is nothing for this component to call.
 */
export function DriverOnboarding({ onComplete, onSkip }: DriverOnboardingProps) {
  const scenes: OnboardingScene[] = [
    {
      id: 'welcome',
      title: 'Как работает PIOS',
      subtitle: 'Ваш рабочий инструмент для работы со своими клиентами.',
      durationMs: 5000,
    },
    {
      id: 'personal-link',
      title: 'Ваша персональная ссылка',
      subtitle: 'Отправьте её своему клиенту.',
      durationMs: 6000,
      content: (
        <div className={styles.demoStack}>
          <DriverCard driverCode="demo-arthur" displayName="Артур" availability="UNAVAILABLE" hideCode />
          <QRCard invitationLink="https://pios.example/i/demo-arthur" />
        </div>
      ),
    },
    {
      id: 'passenger-invitation',
      title: 'Клиент видит, кто его пригласил',
      durationMs: 5000,
      content: (
        <div className={styles.previewScreen}>
          <div className={styles.previewAvatar} aria-hidden="true">
            А
          </div>
          <p className={styles.previewHeadline}>👋 Вас пригласил Артур</p>
          <p className={styles.previewNote}>Экран, который видит клиент, открыв ссылку</p>
        </div>
      ),
    },
    {
      id: 'passenger-order',
      title: 'Клиент отправляет заявку',
      durationMs: 6000,
      content: (
        <div className={styles.previewScreen}>
          <div className={styles.demoField}>
            <span className={styles.demoFieldLabel}>Откуда</span>
            <span className={styles.demoFieldValue}>ул. Ленина, 10</span>
          </div>
          <div className={styles.demoField}>
            <span className={styles.demoFieldLabel}>Куда</span>
            <span className={styles.demoFieldValue}>ул. Мира, 25</span>
          </div>
          <div className={styles.demoButton}>Отправить заявку</div>
        </div>
      ),
    },
    {
      id: 'driver-receives-order',
      title: 'Заявка появляется у вас',
      durationMs: 5000,
      content: (
        <div className={styles.orderCardPreview}>
          <p className={styles.orderCardStatus}>Ожидает вашего решения</p>
          <p className={styles.orderCardLine}>Пассажир: Иван</p>
          <p className={styles.orderCardLine}>Откуда: ул. Ленина, 10</p>
          <p className={styles.orderCardLine}>Куда: ул. Мира, 25</p>
        </div>
      ),
    },
    {
      id: 'ride-lifecycle',
      title: 'PIOS сопровождает заказ от заявки до завершения',
      durationMs: 8000,
      content: (
        <ol className={styles.lifecycleList}>
          <li>Принять</li>
          <li>Прибыл</li>
          <li>Начать поездку</li>
          <li>Завершить поездку</li>
        </ol>
      ),
    },
    {
      id: 'conclusion',
      title: 'PIOS помогает вам работать со своей клиентской базой',
      subtitle: 'Вы строите свой бизнес, используя платформу.',
      durationMs: null,
    },
  ]

  return <OnboardingWalkthrough scenes={scenes} ctaLabel="Начать работу" onComplete={onComplete} onSkip={onSkip} />
}
