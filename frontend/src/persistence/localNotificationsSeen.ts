const STORAGE_KEY = 'pios.notifications.seen'

// A reading position must stay bounded, not become a permanent, ever-growing
// history -- exactly the distinction ADR-071 Part 5 Gap 6 draws ("Acceptable
// for a reading position; it would not be acceptable for a delivery
// record"). A driver's lifetime ride count could otherwise grow this
// without limit. Losing the oldest id once this bound is exceeded simply
// means a very old, already-actioned fact could in principle be treated as
// unseen again -- harmless: [deriveNotificationFacts] never re-emits an
// already-reached state on its own (ADR-071 Part 2's baseline-reseed rule
// already prevents that independently, on every fresh mount).
const MAX_SEEN_IDS = 500

/**
 * ADR-071 (In-App, Poll-Derived Notification Surface), Part 4's "one seam":
 * persists no record of any communication anywhere on the server (the
 * boundary against `ADR-033`'s reserved Notification entity, which Part 4
 * states explicitly). This file is the one place that remembers, on this
 * device only, which already-derived `NotificationFact` ids (see
 * `features/notifications/deriveNotificationFacts.ts`) the driver or
 * passenger has already seen -- a reading position, not a delivery record.
 * Same one-file-per-concern, try/catch-wrapped convention as
 * `localInstallSeen.ts`/`localOnboardingSeen.ts`.
 *
 * Ids are exactly `NotificationFact.id` -- server-derived (`proposalId` +
 * target status, `assignmentId` + target status, or `messageId`), never a
 * client-minted id (ADR-071 Part 4) -- so a given fact's "seen" state is
 * stable across reloads even though the fact list itself is recomputed
 * from scratch, in memory, on every mount (ADR-071 Part 2's own baseline
 * rule: the first poll after mount seeds silently and emits nothing).
 *
 * One shared list, not one per role: a given device is used by either a
 * driver or a passenger in practice (mirrors `localInstallSeen.ts`'s own
 * "one flag, not one per role" reasoning), and fact ids are already
 * globally unique by construction, so no namespacing is needed to avoid a
 * collision between the two.
 */

function readSeenIds(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return []
    }
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === 'string') : []
  } catch {
    return []
  }
}

export function isNotificationSeen(id: string): boolean {
  return readSeenIds().includes(id)
}

export function markNotificationSeen(id: string): void {
  try {
    const current = readSeenIds()
    if (current.includes(id)) {
      return
    }
    const updated = [...current, id].slice(-MAX_SEEN_IDS)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated))
  } catch {
    // Best-effort only -- a fact that can't be marked seen simply shows as
    // unread again next time, same harmless-degradation convention
    // `localInstallSeen.ts`'s own KDoc already documents for itself.
  }
}

/** Same as calling [markNotificationSeen] for every id in [ids], but a single read-modify-write instead of one per id -- used when a driver/passenger opens the notification list and every currently-shown fact becomes seen at once. */
export function markAllNotificationsSeen(ids: string[]): void {
  try {
    const merged = readSeenIds()
    for (const id of ids) {
      if (!merged.includes(id)) {
        merged.push(id)
      }
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(merged.slice(-MAX_SEEN_IDS)))
  } catch {
    // Best-effort only, same reasoning as [markNotificationSeen].
  }
}
