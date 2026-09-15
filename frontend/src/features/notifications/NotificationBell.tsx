import { useState } from 'react'
import type { NotificationFact, NotificationFactKind } from './deriveNotificationFacts'
import styles from './NotificationBell.module.css'

/**
 * Copied verbatim from ADR-071 Part 3's own ratified "Fact" column --
 * this component invents no wording of its own, and adds no fact this
 * ADR does not already name.
 */
const FACT_LABEL: Record<NotificationFactKind, string> = {
  D1: 'Новый заказ ждёт вашего решения',
  D2: 'Пассажир подтвердил вашу цену — поездка состоится',
  D3: 'Пассажир отклонил вашу цену',
  D4: 'Пассажир отменил заказ до вашего ответа',
  D5: 'Пассажир отменил поездку, которую вы приняли',
  D6: 'Новое сообщение от пассажира',
  D7: 'Заказ, на который вы не ответили, больше не активен',
  P1: 'Водитель назвал цену — нужно ваше решение',
  P2: 'Ваша поездка подтверждена',
  P3: 'Водитель отклонил ваш заказ',
  P4: 'Водитель приехал',
  P5: 'Поездка началась',
  P6: 'Поездка завершена',
  P7: 'Новое сообщение от водителя',
}

export interface NotificationBellProps {
  facts: NotificationFact[]
  unseenCount: number
  /** Called when the panel is opened -- the caller marks every currently-shown fact seen (ADR-071 Part 4: no server-side record of what was shown, only this device's own reading position, `localNotificationsSeen.ts`). */
  onOpen: () => void
}

/**
 * ADR-071 (In-App, Poll-Derived Notification Surface) -- the one UI
 * surface this ADR authorizes. Real SVG icon, no emoji (matches this
 * session's own PasswordInput/PassengerLanding fix). The badge is hidden
 * entirely at zero, never rendered as a fabricated "0" (this codebase's
 * own established no-hiding-a-real-zero convention is about not hiding a
 * real fact -- an *absence* of unseen notifications is correctly shown by
 * showing nothing at all, the same way `DriverHome.tsx`'s "Клиенты" tab
 * shows no share-toast text unless something was actually shared).
 */
export function NotificationBell({ facts, unseenCount, onOpen }: NotificationBellProps) {
  const [open, setOpen] = useState(false)

  function toggle() {
    const next = !open
    setOpen(next)
    if (next) {
      onOpen()
    }
  }

  return (
    <div className={styles.wrapper}>
      <button
        type="button"
        className={styles.bellButton}
        aria-label={unseenCount > 0 ? `Уведомления, новых: ${unseenCount}` : 'Уведомления'}
        onClick={toggle}
      >
        <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
          <path d="M5 8a5 5 0 0 1 10 0c0 3 1 4.5 1.5 5H3.5C4 12.5 5 11 5 8Z" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M8.2 15.4a1.8 1.8 0 0 0 3.6 0" strokeLinecap="round" />
        </svg>
        {unseenCount > 0 && (
          <span className={styles.badge} aria-hidden="true">
            {unseenCount > 9 ? '9+' : unseenCount}
          </span>
        )}
      </button>
      {open && (
        <div className={styles.panel} role="dialog" aria-label="Уведомления">
          {facts.length === 0 ? (
            <p className={styles.empty}>Пока ничего нет.</p>
          ) : (
            <ul className={styles.list}>
              {facts.map((fact) => (
                <li key={fact.id} className={styles.item}>
                  {FACT_LABEL[fact.kind]}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
