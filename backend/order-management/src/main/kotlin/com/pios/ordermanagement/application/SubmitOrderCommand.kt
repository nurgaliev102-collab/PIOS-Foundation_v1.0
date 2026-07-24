package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.OrderOrigin

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
 * `destination` was added and then reverted within the same sprint
 * (backward-compatibility correction) — see [com.pios.ordermanagement.domain.Order]'s
 * own KDoc. A future, versioned endpoint reintroduces it.
 */
class SubmitOrderCommand(val origin: OrderOrigin)
