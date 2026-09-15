import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  deriveNotificationFacts,
  type NotificationAssignmentSnapshot,
  type NotificationAudience,
  type NotificationFact,
  type NotificationMessageSnapshot,
  type NotificationOrderSnapshot,
  type NotificationProposalSnapshot,
  type NotificationSnapshot,
} from './deriveNotificationFacts'
import { isNotificationSeen, markAllNotificationsSeen, markNotificationSeen } from '../../persistence/localNotificationsSeen'

export interface UseNotificationFactsResult {
  /** Newest first -- every fact this hook has derived for [audience] since this component mounted. Not persisted across a remount (ADR-071 Part 2's own baseline-reseed rule applies identically here: a fresh mount seeds a fresh baseline and starts empty). */
  facts: NotificationFact[]
  /** How many of [facts] are not yet marked seen (`localNotificationsSeen.ts`) -- 0 renders no badge at all, never a fabricated "0". */
  unseenCount: number
  markSeen: (id: string) => void
  markAllSeen: () => void
}

/**
 * ADR-071's own hook half of "a small hook plus a bell/list UI component".
 * Wraps [deriveNotificationFacts] with the state a real screen needs:
 * remembers the previous poll's snapshot (the baseline [deriveNotificationFacts]
 * itself requires), accumulates the facts derived for [audience] across
 * this mount's own polls, and tracks which of them are already marked
 * seen (`localNotificationsSeen.ts`, this device's own reading position).
 *
 * Takes the four already-polled arrays directly, not a pre-built
 * snapshot object -- `DriverHome.tsx`/`RideRequest.tsx` already hold each
 * of these in their own state (`proposals`, `assignments`, `orderDetails`/
 * `OrderListItem[]`, per-proposal messages); this hook only reshapes them,
 * it never fetches anything itself (ADR-071 Part 1: no new request).
 * The effect below re-derives only when one of these four arrays'
 * identity actually changes (i.e. once per poll, when the calling
 * screen's own `setState` runs) -- not on every unrelated re-render.
 */
export function useNotificationFacts(
  audience: NotificationAudience,
  proposals: NotificationProposalSnapshot[],
  assignments: NotificationAssignmentSnapshot[],
  orders: NotificationOrderSnapshot[],
  messages: NotificationMessageSnapshot[]
): UseNotificationFactsResult {
  const previousSnapshotRef = useRef<NotificationSnapshot | null>(null)
  const [facts, setFacts] = useState<NotificationFact[]>([])
  // Bumped after every markSeen/markAllSeen call so [unseenCount] below
  // (computed by reading `localNotificationsSeen.ts` fresh, not cached in
  // state of its own) is recomputed on the next render -- localStorage
  // itself is not reactive.
  const [seenVersion, setSeenVersion] = useState(0)

  useEffect(() => {
    const current: NotificationSnapshot = { proposals, assignments, orders, messages }
    const previous = previousSnapshotRef.current
    previousSnapshotRef.current = current
    if (previous === null) {
      // ADR-071 Part 2: seed the baseline silently, emit nothing.
      return
    }
    const derived = deriveNotificationFacts(previous, current).filter((fact) => fact.audience === audience)
    if (derived.length === 0) {
      return
    }
    setFacts((existing) => {
      const knownIds = new Set(existing.map((fact) => fact.id))
      const additions = derived.filter((fact) => !knownIds.has(fact.id))
      return additions.length === 0 ? existing : [...additions, ...existing]
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `audience` is
    // effectively constant per screen (DriverHome always passes 'driver',
    // RideRequest always passes 'passenger'); the four arrays are the only
    // deps that ever actually change.
  }, [proposals, assignments, orders, messages, audience])

  const markSeen = useCallback((id: string) => {
    markNotificationSeen(id)
    setSeenVersion((version) => version + 1)
  }, [])

  const markAllSeen = useCallback(() => {
    markAllNotificationsSeen(facts.map((fact) => fact.id))
    setSeenVersion((version) => version + 1)
  }, [facts])

  // Recomputed from `localNotificationsSeen.ts` (never cached in state of
  // its own, so that file stays the one source of truth for "seen") --
  // [seenVersion] is the only reason this memo re-runs after a
  // markSeen/markAllSeen call, since localStorage itself is not reactive.
  const unseenCount = useMemo(
    () => facts.reduce((count, fact) => (isNotificationSeen(fact.id) ? count : count + 1), 0),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [facts, seenVersion]
  )

  return { facts, unseenCount, markSeen, markAllSeen }
}
