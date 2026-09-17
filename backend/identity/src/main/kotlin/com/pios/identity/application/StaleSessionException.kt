package com.pios.identity.application

/**
 * ADR-082 Part 3/§8 (D-03.3) — thrown when a presented token's `sgen`
 * claim no longer matches the live `Identity.sessionGeneration`: the
 * token was minted before a successful recovery bumped the generation.
 * Mapped by [com.pios.identity.api.IdentityController] to 401, the same
 * status an invalid/expired token already produces, so this never becomes
 * a new way to distinguish "this identity exists and recovered recently"
 * from any other authentication failure.
 */
class StaleSessionException : RuntimeException("session generation no longer current")
