package com.pios.passengerexperience.application

/**
 * The REST transport boundary for the Passenger Experience -> Order
 * Management contract (ADR-004, ADR-028; Tranche 2: Passenger Experience
 * REST Transport), mirroring [com.pios.ordermanagement.application.EventPublisher]'s
 * own shape: no storage technology, HTTP client library, or connection
 * detail is named here. Submits [passengerReference] -- ADR-027's
 * already-fixed primitive shape -- and returns the newly created order's
 * id, or throws if the request could not be completed. A connection
 * failure, timeout, or validation failure each propagate as a distinct
 * exception; none is silently converted into a successful result.
 *
 * Only [OrderSubmissionCoordinator] calls this; no other class in this
 * module implements or depends on it.
 */
interface OrderSubmissionClient {
    fun submit(passengerReference: String): String
}
