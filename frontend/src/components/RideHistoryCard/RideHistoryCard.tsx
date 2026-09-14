import { Card } from '../Card'
import { Text } from '../Text'
import { RideStatus } from '../RideStatus'
import styles from './RideHistoryCard.module.css'

export interface RideHistoryCardProps {
  /** Already formatted, e.g. "14 сентября, 15:32" -- this component owns no date/time logic of its own (docs/PIOS_DESIGN_SYSTEM.md Section 5, "Text owns no formatting logic"). */
  dateTime: string | null
  /** "Откуда → Куда", or a single side, or `null` if neither address is on file -- the caller decides the exact string, this component only renders it. */
  route: string | null
  /** The other party's own name, or an honest generic label ("Пассажир"/"Водитель") when none is on file -- never a raw id (mirrors `DriverHome.tsx`'s own `shortOrderCode` discipline: no UUID ever shown to a person). */
  counterpart: string
  /** Already formatted with a currency-free unit exactly as the driver typed it (`Proposal.statedPrice` is a plain string, ADR-042) -- `null` renders an honest "not stated" caption instead of fabricating a number. */
  price: string | null
}

/**
 * PIOS design system — RideHistoryCard (MVP completion, History §1). The
 * `RideCard` docs/PIOS_DESIGN_SYSTEM.md Section 5 already named as a
 * future primitive ("the canonical list-item for history and active-ride
 * summaries") — built now, for the first real history list this product
 * ships (driver's own "Маршруты" tab, previously a permanent placeholder).
 * A single completed ride, read-only: date/time, route, counterpart, price,
 * and a terminal `RideStatus` badge — no actions, this is a record, not a
 * decision point.
 */
export function RideHistoryCard({ dateTime, route, counterpart, price }: RideHistoryCardProps) {
  return (
    <Card tone="muted">
      <div className={styles.header}>
        <Text role="body" strong>
          {counterpart}
        </Text>
        <RideStatus status="COMPLETED" label="Завершена" />
      </div>
      {route && <Text role="body">{route}</Text>}
      <div className={styles.footer}>
        {dateTime && (
          <Text role="caption" tone="secondary">
            {dateTime}
          </Text>
        )}
        {/* Rendered verbatim, no appended currency symbol -- `statedPrice`
            is a plain, driver-typed string (ADR-042), which may already
            include its own unit (the input's own placeholder suggests
            "300 ₽", but nothing enforces it) -- appending one here could
            double it up. Mirrors `ProposalDetails`' own identical
            "Стоимость: {proposal.statedPrice}" convention exactly. */}
        <Text role="numeric" tone="primary" strong>
          {price ?? 'Цена не указана'}
        </Text>
      </div>
    </Card>
  )
}
