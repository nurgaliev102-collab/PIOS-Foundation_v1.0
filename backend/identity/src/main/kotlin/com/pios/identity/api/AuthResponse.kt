package com.pios.identity.api

/**
 * The response shape ADR-055 Decision 3 specifies for both
 * `POST /v1/identities/register` and `POST /v1/identities/login`.
 * [expiresAt] follows [IdentityResponse]'s sibling convention
 * (`ConnectionResponse.createdAt`'s own explicit `Instant.toString()`,
 * ISO-8601) rather than depending on Jackson's automatic `Instant`
 * (de)serialization.
 */
data class AuthResponse(val identityId: String, val driverId: String?, val token: String, val expiresAt: String)
