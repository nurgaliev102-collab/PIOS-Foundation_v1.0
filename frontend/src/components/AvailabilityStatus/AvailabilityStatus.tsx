import { Card } from '../Card'
import { Text } from '../Text'
import { Button } from '../Button'
import { StatusMessage } from '../StatusMessage'
import styles from './AvailabilityStatus.module.css'

export interface AvailabilityStatusProps {
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  onToggle: () => void
  /**
   * True while the toggle request is in flight — disables the button and
   * shows `Button`'s own spinner (docs/PIOS_DESIGN_SYSTEM.md Section 6,
   * "loading"), the label text itself stays constant. Matches the
   * already-established convention `RideRequest`'s own `Button` usage
   * set (Task 4, docs/PIOS_DESIGN_IMPLEMENTATION_LOG.md Section 4:
   * "Loading-state buttons now show a spinner instead of changing their
   * label text") — deliberately not DriverHome's pre-migration
   * "Обновляем…" label-swap.
   */
  loading?: boolean
  /** True if the last toggle attempt failed — same wording DriverHome already used before this component existed. */
  error?: boolean
}

/**
 * PIOS design system — AvailabilityStatus (docs/PIOS_DESIGN_SYSTEM.md
 * Section 5, "generalizing `DriverCard`'s existing `.available`/
 * `.unavailable` pill"; Section 15's own suggested first migration
 * target for `DriverHome`). This is the driver's own single,
 * unambiguous, driver-controlled toggle (brief Section 6) — copy is
 * owned by this component, matching `DriverTrustIndicator`'s own
 * precedent of owning its microcopy ("Доступен"/"Недоступен"), since
 * this text is specific to exactly this control and not meant to vary
 * by caller.
 *
 * The dot uses `--pios-color-success` for available and
 * `--pios-color-text-muted` for unavailable — deliberately the same two
 * tokens `DriverTrustIndicator`'s own availability dot already uses
 * (that component's own `.dotAvailable`/`.dotUnavailable`), not the
 * alarm-red emoji DriverHome used before this component existed. A
 * driver choosing not to work today is a neutral, their-own-call state
 * (brief Section 6: "entirely their call... never nudged"), not an
 * error condition — red specifically implies something is wrong, which
 * this replaces with the same muted grey the rest of the design system
 * already uses for "not currently available" everywhere else.
 */
export function AvailabilityStatus({ availability, onToggle, loading = false, error = false }: AvailabilityStatusProps) {
  const isAvailable = availability === 'AVAILABLE'
  return (
    <Card>
      <div className={styles.row}>
        <span className={isAvailable ? styles.dotAvailable : styles.dotUnavailable} aria-hidden="true" />
        <Text role="body" strong>
          {isAvailable ? 'Я на линии' : 'Сегодня не работаю'}
        </Text>
      </div>
      <Text role="caption" tone="secondary">
        {isAvailable ? 'Вы можете получать заказы' : 'Вы не получаете новые заказы'}
      </Text>
      <div className={styles.actionRow}>
        <Button
          label={isAvailable ? 'Уйти с линии' : 'Выйти на линию'}
          variant="primary"
          loading={loading}
          onClick={onToggle}
        />
      </div>
      {error && <StatusMessage tone="error">Не удалось обновить. Попробуйте ещё раз.</StatusMessage>}
    </Card>
  )
}
