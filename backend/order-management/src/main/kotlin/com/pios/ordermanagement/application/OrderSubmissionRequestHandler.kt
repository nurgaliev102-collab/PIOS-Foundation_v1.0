package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderOrigin
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Consumer-side handler for the Passenger Experience -> Order Management
 * contract (INTERFACE_CONTRACTS.md Section 5; ADR-027). Accepts the
 * originating passenger reference as a plain primitive — never Passenger
 * Experience's own `PassengerReference` type — and starts Order
 * Management's own Submit Order handling (DOMAIN_MODEL.md Section 9).
 *
 * Sprint FND-006 (Minimal Order Model): [passengerReference] is no
 * longer discarded — it becomes this order's own [OrderOrigin], a type
 * local to Order Management (see that class's own KDoc for why this does
 * not reintroduce INTERFACE_CONTRACTS.md Section 5's "no copy of that
 * representation becomes Order Management's own information": what is
 * stored is Order Management's own fact, in its own type, not a copy of
 * Passenger Experience's `PassengerReference`). [OrderOrigin] validates
 * its own content on construction (non-blank); this handler performs no
 * duplicate validation of its own, consistent with this project's "No
 * Business Logic Duplication" principle (APPLICATION_ARCHITECTURE.md
 * Section 2).
 *
 * A `destination` parameter was added here and then reverted within the
 * same sprint (backward-compatibility correction) — see
 * [com.pios.ordermanagement.domain.Order]'s own KDoc for why. Sprint 3B
 * (MVR Pilot Enablement) reintroduces it as [destination], an optional
 * parameter defaulting to `null` — every existing caller of [handle] that
 * supplies only [passengerReference] continues to compile and behave
 * unchanged.
 *
 * [pickupAddress] (Sprint H5: Entrepreneur Working Cycle Integrity) follows
 * the same optional, defaulting-to-`null`, appended-last shape for the same
 * backward-compatibility reason.
 *
 * This handler makes no call into any other module (ADR-027); its
 * compatibility with Passenger Experience's provider representation
 * (`OrderSubmissionRequestedPublisher`) is verified only from test code
 * holding a test-scoped dependency on this module.
 */
@Service
class OrderSubmissionRequestHandler(
    private val orderLifecycleApplicationService: OrderLifecycleApplicationService
) {
    fun handle(
        passengerReference: String,
        destination: String? = null,
        passengerName: String? = null,
        pickupAddress: String? = null,
        requestedPickupAt: String? = null,
        isTest: Boolean = false,
        explicitDriverIntent: Boolean = false,
        passengerCount: Int? = null
    ): String {
        val submitted = orderLifecycleApplicationService.submitOrder(
            SubmitOrderCommand(
                origin = OrderOrigin(passengerReference),
                destination = destination,
                passengerName = passengerName,
                pickupAddress = pickupAddress,
                requestedPickupAt = parseRequestedPickupAt(requestedPickupAt),
                isTest = isTest,
                explicitDriverIntent = explicitDriverIntent,
                passengerCount = passengerCount
            )
        )
        return submitted.order.id.value
    }

    /**
     * Parses the wire-format [requestedPickupAt] (ADR-058 Decision item 4)
     * to an [Instant]. A value that does not parse is surfaced as
     * [IllegalArgumentException] — the same exception type this handler's
     * own [OrderOrigin] construction already throws for a blank passenger
     * reference — so [OrderSubmissionController] maps it to the same 400
     * without a new catch clause.
     */
    private fun parseRequestedPickupAt(requestedPickupAt: String?): Instant? {
        if (requestedPickupAt == null) return null
        return try {
            Instant.parse(requestedPickupAt)
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("requestedPickupAt must be a valid ISO-8601 instant", ex)
        }
    }
}
