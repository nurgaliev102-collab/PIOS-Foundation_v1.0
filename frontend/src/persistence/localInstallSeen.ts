const INSTALL_HELP_KEY = 'pios.install.help-seen'

/**
 * PIOS Install v1 — a single flag, deliberately its own key, never
 * `pios.onboarding.driver-seen`/`pios.onboarding.passenger-seen` (Section
 * 14's own explicit instruction): onboarding and install-help are shown
 * and dismissed for two different reasons, and a shared key would make
 * dismissing one silently suppress the other. Same one-file-per-concern,
 * try/catch-wrapped convention as `localOnboardingSeen.ts`/
 * `localDisplayName.ts`/`localCurrentOrder.ts`.
 *
 * One flag, not one per role: unlike onboarding, install instructions are
 * identical regardless of whether the person is a driver or a passenger
 * (same device, same browser, same steps) — a second key would track a
 * distinction the instructions themselves don't make.
 */

export function hasSeenInstallHelp(): boolean {
  try {
    return localStorage.getItem(INSTALL_HELP_KEY) === 'true'
  } catch {
    return false
  }
}

export function markInstallHelpSeen(): void {
  try {
    localStorage.setItem(INSTALL_HELP_KEY, 'true')
  } catch {
    // Best-effort only — a person who can't persist this simply sees the
    // install card again next time, which is harmless.
  }
}
