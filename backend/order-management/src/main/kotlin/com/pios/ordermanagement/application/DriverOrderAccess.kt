package com.pios.ordermanagement.application

/**
 * Authorization projection owned by Dispatch: returns the orders that
 * have actually been offered to the authenticated driver. Order
 * Management owns order PII, but it must not infer driver access merely
 * from a caller-supplied order id.
 */
fun interface DriverOrderAccess {
    fun accessibleOrderIds(driverId: String, authorization: String): Set<String>
}

class DriverOrderAccessUnavailableException(cause: Throwable) :
    RuntimeException("Dispatch order-access verification is unavailable", cause)
