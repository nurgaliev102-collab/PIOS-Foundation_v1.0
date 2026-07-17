package com.pios.ordermanagement.application

/**
 * The Submit Order command (DOMAIN_MODEL.md Section 9;
 * MODULE_STRUCTURE.md, "Order Management Module", Owned capabilities).
 * Represents the intention to submit a new order. It carries no fields at
 * this scope: the originating passenger or corporate customer context
 * (INTERFACE_CONTRACTS.md, Contract: Passenger Experience → Order
 * Management) and Order Origin (DOMAIN_MODEL.md Section 6) are not part of
 * this task's scope and are not represented here.
 */
class SubmitOrderCommand
