package com.pios.dispatch.application

import com.pios.dispatch.domain.OrderReference

interface OrderGuard {
    /** Must be called inside the caller's Dispatch transaction, before any other row lock. */
    fun lock(order: OrderReference)
}

object NoOpOrderGuard : OrderGuard {
    override fun lock(order: OrderReference) = Unit
}
