/**
 * Bug found 2026-09-05 (`docs/PIOS_PRODUCT_EVIDENCE.md` E-001): a real
 * registration attempt from an iPhone, over the public Tailscale Funnel,
 * failed with a generic "проверьте связь с интернетом" message while the
 * network was entirely fine. Root cause: a `type="tel"` input readily picks
 * up spaces/dashes (iOS's own keyboard/autofill formatting), while the
 * identity backend's own `Phone` value class
 * (`backend/identity/src/main/kotlin/com/pios/identity/domain/Phone.kt`)
 * requires an exact `^\+[1-9][0-9]{6,14}$` match — any deviation was a
 * silent HTTP 400 (`IdentityController.register`'s own `catch
 * (ex: IllegalArgumentException)` branch, which logs nothing), which the
 * frontend's own catch-all then reported as a network problem.
 *
 * [PHONE_PATTERN] mirrors that backend regex exactly. It is duplicated
 * here, not imported, because no shared contract package exists between
 * frontend and backend today (same situation `docs/PIOS_PATH_TO_PUBLIC_LAUNCH.md`
 * Part B notes elsewhere) — if the backend's own pattern ever changes,
 * this one must be updated to match by hand.
 */
const PHONE_PATTERN = /^\+[1-9][0-9]{6,14}$/

/**
 * Strips only characters that are unambiguously formatting noise — spaces,
 * dashes, parentheses — the exact shapes a phone keypad's own autofill adds.
 * Deliberately does **not** guess a country code (e.g. turning a leading
 * "8" into "+7"): that is a real assumption about the user's country this
 * function has no basis for making, so a number missing its "+" prefix
 * still fails [isValidPhone] afterward, with a message asking the person to
 * supply it themselves.
 */
export function normalizePhone(input: string): string {
  return input.replace(/[\s\-()]/g, '')
}

/** True only for a phone already in the exact shape the backend accepts. */
export function isValidPhone(phone: string): boolean {
  return PHONE_PATTERN.test(phone)
}

/** The one user-facing explanation for what a valid phone looks like — shared so `DriverHome.tsx`/`PassengerLanding.tsx` never drift into two different wordings for the same requirement. */
export const PHONE_FORMAT_HINT = 'Введите номер телефона в международном формате, например +79991234567 (только цифры после «+»).'
