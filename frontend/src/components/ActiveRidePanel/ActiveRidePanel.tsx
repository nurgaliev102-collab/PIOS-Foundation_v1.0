import type { ReactNode } from 'react'
import { Card } from '../Card'
import { Text } from '../Text'
import { Button } from '../Button'
import { StatusMessage } from '../StatusMessage'
import { RideStatus, type RideLifecycleStatus } from '../RideStatus'
import styles from './ActiveRidePanel.module.css'

export interface ActiveRidePanelProps {
  orderCode: string
  status: RideLifecycleStatus
  statusLabel: string
  details: ReactNode
  /**
   * Omitted entirely (not disabled) while the driver's own next action
   * isn't known yet — mirrors `DriverHome`'s own pre-existing
   * `assignment && (...)` gate exactly: no button at all, not a disabled
   * one, until the Assignment this ride belongs to has actually loaded.
   */
  primaryAction?: { label: string; onClick: () => void }
  submitting: boolean
  error: boolean
}

/**
 * PIOS design system — ActiveRidePanel (docs/PIOS_DESIGN_SYSTEM.md
 * Section 5: "the `Sheet`-based ... surface for an in-progress ride" —
 * built here as an elevated `Card`, not a `Sheet`, since `Sheet` itself
 * is out of this task's own scope and this panel renders inline in the
 * existing page flow, not as an overlay). Closes the exact gap the PIOS
 * design continuation audit (Task 8's own report, not a committed file)
 * named: an accepted
 * proposal progressing through arrive→start→complete rendered in the
 * identical container as a still-undecided request, with nothing marking
 * it as the driver's *current* ride. Brief Section 6: "kept minimal,
 * since this is a moment the driver is often not looking at their
 * phone" — one `RideStatus` badge, the trip's own details, and exactly
 * one primary action; no secondary controls compete for attention here.
 *
 * Deliberately does not reuse `RequestCard`'s own accent-colored rule —
 * this needs its own, different visual signal (`--pios-color-success`,
 * matching `RideStatus`'s own tone for every one of this panel's
 * possible statuses) so a driver can tell "needs a decision" and "this
 * is your active ride" apart at a glance, without reading either card's
 * own text first.
 */
export function ActiveRidePanel({ orderCode, status, statusLabel, details, primaryAction, submitting, error }: ActiveRidePanelProps) {
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

        {primaryAction && (
          <div className={styles.actionRow}>
            <Button label={primaryAction.label} variant="primary" loading={submitting} onClick={primaryAction.onClick} />
          </div>
        )}

        {error && <StatusMessage tone="error">Не удалось обновить статус поездки. Попробуйте ещё раз.</StatusMessage>}
      </Card>
    </div>
  )
}
