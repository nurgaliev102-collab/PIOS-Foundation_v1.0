import { StatusMessage, type StatusTone } from '../StatusMessage'

/**
 * Every stage a Proposal/Assignment pair moves through, from either
 * side's own point of view — `CREATED` exists only on the driver's side
 * (an Assignment that exists but hasn't been marked "Прибыл" yet;
 * `RideRequest.tsx`'s own passenger-facing status already collapses this
 * into `ACCEPTED`, since a passenger has no separate "arrived" concept
 * to distinguish it from). A superset of `RideRequest.tsx`'s own,
 * previously local `RideStatus` type, extended only to add the one value
 * that type never needed.
 */
export type RideLifecycleStatus =
  | 'OPEN'
  | 'PRICE_PROPOSED'
  | 'DECLINED'
  | 'LAPSED'
  | 'WITHDRAWN'
  | 'CREATED'
  | 'ACCEPTED'
  | 'ARRIVED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'TERMINATED'
  | 'UNFULFILLED'

export interface RideStatusProps {
  status: RideLifecycleStatus
  /**
   * The actual, audience-facing text — owned by the caller, not this
   * component. A driver and a passenger see different wording for the
   * same underlying stage (e.g. `OPEN` reads "Ожидает вашего решения" on
   * `DriverHome`, "Ждём ответа водителя…" on `RideRequest`) — that
   * difference is real product copy, not a visual decision, so this
   * component never invents or duplicates it (mirrors `RideStatus`'s own
   * spec, docs/PIOS_DESIGN_SYSTEM.md Section 5: "no component-level
   * opinion" beyond the visual tone).
   */
  label: string
}

const toneForStatus: Record<RideLifecycleStatus, StatusTone> = {
  OPEN: 'information',
  // A price was named and now needs a decision -- not yet a success
  // (nothing is settled) and not a problem, so it gets its own distinct
  // "needs attention" tone rather than borrowing OPEN's or ACCEPTED's own.
  PRICE_PROPOSED: 'warning',
  DECLINED: 'error',
  LAPSED: 'warning',
  WITHDRAWN: 'warning',
  CREATED: 'success',
  ACCEPTED: 'success',
  ARRIVED: 'success',
  IN_PROGRESS: 'success',
  COMPLETED: 'success',
  TERMINATED: 'warning',
  UNFULFILLED: 'error',
}

/**
 * PIOS design system — RideStatus (docs/PIOS_DESIGN_SYSTEM.md Section 5:
 * "the single shared status-state component for requested/matched/
 * arriving/in-progress/completed/cancelled/declined, used identically by
 * both `DriverIdentity`-adjacent passenger views and driver views" —
 * brief acceptance criterion Section 13 item 3). A thin wrapper over
 * `StatusMessage`, whose own KDoc already names this as its reason for
 * existing ("this is the shared primitive `RideStatus`-shaped text
 * uses") — this component owns only the status→tone mapping, in exactly
 * one place, so `DriverHome` and `RideRequest` can never drift into two
 * different color meanings for the same underlying stage the way two
 * separately-maintained tone functions risked.
 */
export function RideStatus({ status, label }: RideStatusProps) {
  return <StatusMessage tone={toneForStatus[status]}>{label}</StatusMessage>
}
