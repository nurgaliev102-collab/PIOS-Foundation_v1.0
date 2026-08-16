package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderOrigin
import java.time.Instant

/**
 * The Submit Order command (DOMAIN_MODEL.md Section 9;
 * MODULE_STRUCTURE.md, "Order Management Module", Owned capabilities).
 * Represents the intention to submit a new order.
 *
 * Carries [origin] (Sprint FND-006: Minimal Order Model) — already a
 * typed domain value by the time this command exists; this command does
 * not itself validate its content, only carries what
 * [com.pios.ordermanagement.domain.Order.submit] already requires.
 *
 * Carries [destination] (Sprint 3B: MVR Pilot Enablement — Optional
 * Destination), defaulting to `null` — see
 * [com.pios.ordermanagement.domain.Order]'s own KDoc for why it is a
 * plain, optional, unvalidated `String?` rather than a typed domain
 * value like [origin].
 *
 * Carries [passengerName] (first-pilot feedback), defaulting to `null` for
 * the same reason [destination] does — see [com.pios.ordermanagement.domain.Order]'s
 * own KDoc.
 *
 * Carries [pickupAddress] (Sprint H5: Entrepreneur Working Cycle
 * Integrity), defaulting to `null` for the same reason — see
 * [com.pios.ordermanagement.domain.Order]'s own KDoc.
 */
class SubmitOrderCommand(
    val origin: OrderOrigin,
    val destination: String? = null,
    val passengerName: String? = null,
    val pickupAddress: String? = null,
    val requestedPickupAt: Instant? = null
)
