/**
 * ADR-038, Этап 5: the seam for "let the driver pick clients from their
 * device's own contacts" — deliberately unimplemented. No concrete
 * provider backed by the real Contact Picker API, iOS, or Android exists
 * in this codebase yet; this interface exists only so that work has a
 * defined shape to target.
 *
 * `docs/PIOS_IDENTITY_ARCHITECTURE_RESEARCH.md` Section 4 already found
 * the platform split this interface has to accommodate: the Contact
 * Picker API exists only on Chrome/Android (`navigator.contacts`), not on
 * iOS Safari or iOS PWAs at all. [isAvailable] exists specifically so a
 * caller can check before offering the feature, rather than the feature
 * failing silently or a caller assuming it universally.
 *
 * Deliberately NOT included here: any notion of "which of these contacts
 * is already a PIOS user." That is a matching/discovery concern, not a
 * picking concern, and the same research report found the naive way to
 * build it (bulk hash-and-upload, WhatsApp/Telegram-style) is
 * cryptographically broken — phone numbers can be brute-forced in under
 * 150 seconds. A real matching interface, if ever built, depends on
 * wiring this to `network-management`'s `Person`/`Connection`, which
 * ADR-037 already reserved for its own future ADR — inventing that
 * interface here, ahead of that decision, would be exactly the kind of
 * speculative abstraction this project's own engineering principles warn
 * against.
 */
export interface DeviceContact {
  name: string
  phone: string
}

export interface ContactProvider {
  isAvailable(): boolean
  pickContacts(): Promise<DeviceContact[]>
}

/**
 * The only implementation today. Reports itself unavailable and rejects
 * any pick attempt — not a silent no-op, so a future caller cannot
 * mistake "not implemented" for "the user picked nothing."
 */
export class UnimplementedContactProvider implements ContactProvider {
  isAvailable(): boolean {
    return false
  }

  pickContacts(): Promise<DeviceContact[]> {
    return Promise.reject(
      new Error('ContactProvider is not implemented yet (ADR-038) — Contact Picker integration is out of scope.')
    )
  }
}
