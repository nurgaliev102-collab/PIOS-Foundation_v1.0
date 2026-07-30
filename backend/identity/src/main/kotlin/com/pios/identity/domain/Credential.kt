package com.pios.identity.domain

/**
 * Documents the future shape of "a way to prove control of an [Identity]"
 * (ADR-038, Этап 3) — deliberately has **no concrete subtype yet**. This
 * compiles today with nothing implementing it; that is intentional, not an
 * oversight. Adding the first real subtype (a passkey credential, an
 * SMS-verified session credential, a Telegram-verified credential — see
 * `docs/PIOS_IDENTITY_ARCHITECTURE_RESEARCH.md` Section 2 for the
 * candidates this maps to) is explicitly out of scope for this ADR and
 * requires its own follow-up decision: this file exists so that future
 * decision has a real interface to extend, not to pre-select which
 * mechanism wins.
 *
 * No [com.pios.identity.application] service references this type. No
 * database table backs it. It is reachable only by reading this file.
 */
sealed interface Credential
