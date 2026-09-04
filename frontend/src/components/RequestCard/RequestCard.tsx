import type { ReactNode } from 'react'
import { Card } from '../Card'
import { Text } from '../Text'
import { Button } from '../Button'
import { StatusMessage } from '../StatusMessage'
import { RideStatus, type RideLifecycleStatus } from '../RideStatus'
import styles from './RequestCard.module.css'

export interface RequestCardProps {
  orderCode: string
  status: RideLifecycleStatus
  statusLabel: string
  /** Order/passenger detail lines, already formatted by the caller (docs/PIOS_DESIGN_SYSTEM.md Section 5, `Text` owns no formatting logic of its own) — rendered in the order given, each only if non-null. */
  details: ReactNode
  /** The two decision actions — both required, since a request without a way to answer it is not this component's own concern to guard against. */
  onAccept: () => void
  onDecline: () => void
  submitting: boolean
  error: boolean
  /** The price/ETA inputs a driver may fill in before accepting (ADR-042/ADR-057) — presentation-only slot, all state/logic stays with the caller. */
  children?: ReactNode
}

/**
 * PIOS design system — RequestCard (docs/PIOS_DESIGN_SYSTEM.md Section 5:
 * "the driver-side incoming-request surface; composes `Card` + identity
 * + a two-action (accept/decline) `Button` pair"). Built for exactly one
 * concrete gap the PIOS design continuation audit (Task 8's own report,
 * not a committed file) named: an `OPEN` proposal on `DriverHome` rendered
 * identically to every other
 * status, with nothing marking it as *requiring a decision*. `elevated`
 * (Card's own raised-shadow treatment, reserved for something genuinely
 * above the rest of the screen) plus an accent-colored left rule
 * (this component's own CSS, not a token that exists elsewhere — the
 * "this needs you" visual cue the audit found missing) together do that
 * work; the `RideStatus` badge itself already carries the driver-facing
 * "Ожидает вашего решения" copy DriverHome already had, unchanged.
 */
export function RequestCard({
  orderCode,
  status,
  statusLabel,
  details,
  onAccept,
  onDecline,
  submitting,
  error,
  children,
}: RequestCardProps) {
  return (
    <div className={styles.wrap}>
      <Card elevated>
        <div className={styles.header}>
          <Text role="label" tone="secondary">
            Заказ №{orderCode}
          </Text>
          <RideStatus status={status} label={statusLabel} />
        </div>

        {details}

        {children}

        <div className={styles.actions}>
          <Button label="Принять" variant="primary" loading={submitting} onClick={onAccept} />
          <Button label="Отклонить" variant="secondary" loading={submitting} onClick={onDecline} />
        </div>

        {error && <StatusMessage tone="error">Не удалось обновить заказ. Попробуйте ещё раз.</StatusMessage>}
      </Card>
    </div>
  )
}
