package com.pios.dispatch.api

/** The REST response body for `GET /v1/driver-push-subscriptions/public-key` (ADR-083, D-10). */
data class DriverPushPublicKeyResponse(val publicKey: String)
