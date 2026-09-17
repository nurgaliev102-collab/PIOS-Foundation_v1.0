package com.pios.identity.api

/**
 * [phoneVerified] (ADR-082, D-03) is `true` once `Identity.phoneVerifiedAt`
 * is set — additive, never `null`, so existing callers reading only
 * `id`/`phone`/`driverId` are unaffected.
 */
data class IdentityResponse(val id: String, val phone: String?, val driverId: String?, val phoneVerified: Boolean = false)
