package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.OrderReference

/**
 * The Assign Order command (DOMAIN_MODEL.md Section 9;
 * MODULE_STRUCTURE.md, "Dispatch Module", Owned capabilities). Represents
 * the intention to connect a specific order to a specific driver. It does
 * not itself decide whether that connection is valid — that remains the
 * Assignment aggregate's own decision (APPLICATION_ARCHITECTURE.md
 * Section 2, "Domain Decides Business Meaning"). The order and driver
 * references are supplied by the caller: determining which order is
 * submitted and which driver is available belongs to Order Management and
 * Driver Management respectively, not to Dispatch (MODULE_STRUCTURE.md).
 */
data class AssignOrderCommand(
    val order: OrderReference,
    val driver: DriverReference,
    val isTest: Boolean = false
)
